package com.portfolio.aicontentstudio.modules.content.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.ai_log.service.AiUsageLogService;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;
import com.portfolio.aicontentstudio.modules.content.dto.GenerateContentRequest;
import com.portfolio.aicontentstudio.modules.content.dto.PromptConfig;
import com.portfolio.aicontentstudio.modules.content.dto.SeoMetadata;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateBannerRequest;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateContentRequest;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;
import com.portfolio.aicontentstudio.modules.content.mapper.ContentMapper;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentServiceImpl implements ContentService {

    private static final double MAX_SCORE_WITHOUT_H1 = 60.0;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a Senior SEO Copywriter.
            Write compelling, high-converting marketing content for the requested platform.
            Output only the final content body with clear headings when appropriate.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            Generate marketing content with the following requirements:
            - Platform: {platform}
            - Tone: {tone}
            - Main keyword: {keyword}
            - Length: {length_limit}
            - Language: {language}
            """;

    private final CampaignRepository campaignRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;
    private final ChatModel chatModel;
    private final AiUsageLogService aiUsageLogService;

    @Value("${app.ai.model-name:gemini-3-flash-preview}")
    private String configuredModelName;

    @Override 
    @Transactional
    public ContentResponse generateContent(GenerateContentRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Campaign campaign = campaignRepository.findByIdAndUserId(request.campaignId(), userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAMPAIGN_NOT_FOUND));

        ChatResponse chatResponse;
        try {
            chatResponse = chatModel.call(buildPrompt(request));
        } catch (Exception ex) {
            log.error("AI generation failed for userId={}, campaignId={}: {}", userId, request.campaignId(), ex.getMessage());
            throw new AppException(ErrorCode.AI_GENERATION_FAILED);
        }

        String generatedText = extractGeneratedText(chatResponse);
        Content savedContent = contentRepository.save(Content.builder()
                .campaign(campaign)
                .user(user)
                .targetKeyword(request.keyword())
                .promptConfig(buildPromptConfig(request))
                .generatedText(generatedText)
                .seoMetadata(null)
                .status(ContentStatus.DRAFT)
                .build());

        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata != null ? metadata.getUsage() : null;
        aiUsageLogService.logUsage(
                user,
                savedContent,
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null,
                resolveModelName(metadata)
        );

        log.info("Generated content saved: id={}, campaignId={}, userId={}", savedContent.getId(), campaign.getId(), userId);
        return contentMapper.toResponse(savedContent);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentResponse> getContentsByCampaign(UUID campaignId, UUID userId, Pageable pageable) {
        if (!campaignRepository.existsByIdAndUserId(campaignId, userId)) {
            throw new AppException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        return contentRepository.findAllByCampaignIdAndUserId(campaignId, userId, pageable)
                .map(contentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentResponse getContentById(UUID id, UUID userId) {
        return contentMapper.toResponse(findOwnedContent(id, userId));
    }

    @Override
    @Transactional
    public ContentResponse updateContent(UUID id, UpdateContentRequest request, UUID userId) {
        Content content = findOwnedContent(id, userId);
        content.setGeneratedText(request.generatedText());
        content.setSeoMetadata(sanitizeSeoMetadata(request.seoMetadata()));

        Content saved = contentRepository.save(content);
        log.info("Content updated: id={}, userId={}", saved.getId(), userId);
        return contentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ContentResponse updateBanner(UUID id, UpdateBannerRequest request, UUID userId) {
        Content content = findOwnedContent(id, userId);
        content.setBannerUrl(request.bannerUrl());

        Content saved = contentRepository.save(content);
        log.info("Banner updated for contentId={}, userId={}", saved.getId(), userId);
        return contentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteContent(UUID id, UUID userId) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTENT_NOT_FOUND));

        if (!content.getCampaign().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.CONTENT_NOT_FOUND);
        }

        content.setDeletedAt(java.time.LocalDateTime.now());
        contentRepository.save(content);
        log.info("Soft-deleted content item: id={}, userId={}", id, userId);
    }

    private Prompt buildPrompt(GenerateContentRequest request) {
        Map<String, Object> model = Map.of(
                "platform", request.platform(),
                "tone", request.tone(),
                "keyword", request.keyword(),
                "length_limit", request.lengthLimit() != null ? request.lengthLimit() + " words" : "platform-appropriate",
                "language", request.language()
        );

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE);
        PromptTemplate userPromptTemplate = new PromptTemplate(USER_PROMPT_TEMPLATE);
        return new Prompt(List.of(
                systemPromptTemplate.createMessage(),
                userPromptTemplate.createMessage(model)
        ));
    }

    private PromptConfig buildPromptConfig(GenerateContentRequest request) {
        return new PromptConfig(
                request.platform(),
                request.tone(),
                request.lengthLimit() != null ? request.lengthLimit().toString() : "auto",
                request.language()
        );
}

    private String extractGeneratedText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            throw new AppException(ErrorCode.AI_GENERATION_FAILED);
        }

        String content = chatResponse.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new AppException(ErrorCode.AI_GENERATION_FAILED);
        }

        return content.trim();
    }

    private String resolveModelName(ChatResponseMetadata metadata) {
        if (metadata != null && metadata.getModel() != null && !metadata.getModel().isBlank()) {
            return metadata.getModel();
        }
        return configuredModelName;
    }

    private Content findOwnedContent(UUID id, UUID userId) {
        return contentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTENT_NOT_FOUND));
    }

    private SeoMetadata sanitizeSeoMetadata(SeoMetadata seoMetadata) {
        if (seoMetadata == null) {
            return null;
        }

        double sanitizedScore = seoMetadata.score();
        if (seoMetadata.score() > 80.0 && !seoMetadata.hasH1()) {
            sanitizedScore = Math.min(seoMetadata.score(), MAX_SCORE_WITHOUT_H1);
        }

        return new SeoMetadata(
                sanitizedScore,
                seoMetadata.keywordDensity(),
                seoMetadata.hasH1(),
                seoMetadata.suggestion()
        );
    }
}

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
        You are a Senior SEO Copywriter and On-Page SEO Specialist.
        Write publish-ready marketing content that reads naturally and is optimized to score as high as possible on this SEO checklist:

        - The exact main keyword must appear in the H1.
        - Keyword density should be roughly between 1% and 3%.
        - Include at least two H2 headings.
        - Content should exceed 300 words when the platform supports article-style writing.
        - Meta title must be 50-60 characters and include the exact main keyword.
        - Meta description must be 120-160 characters and include the exact main keyword.

        Follow these rules strictly:
        - Return only the final content with no explanation, no commentary, and no labels.
        - Do not output labels such as "Meta Title", "Meta Description", "H1", or "CTA".
        - Do not output XML, JSON, tags, brackets, or code fences.
        - Write in the requested language.
        - Sound natural, persuasive, and editorial, not robotic.
        - Avoid keyword stuffing, but ensure the exact main keyword appears naturally in strategic places.
        - Include exactly one H1 heading using Markdown syntax with bold text: # **Heading**
        - Include at least two H2 headings using Markdown syntax with bold text: ## **Heading**
        - Make the body scannable, specific, conversion-oriented, and visually engaging in an editor.
        - Keep paragraphs short, usually 2 to 4 sentences.
        - Add at least one Markdown bullet list when it fits naturally.
        - Bold the H1 and H2 heading text, and bold 2 to 4 additional important phrases naturally using Markdown bold syntax.
        - Avoid large walls of text and keep a strong visual rhythm.
        - End with a concise CTA when appropriate.

        Output format must be exactly:
        1. First line: meta title only
        2. Second line: meta description only
        3. Third line: blank line
        4. Fourth line onward: Markdown content body beginning with exactly one H1

        Hard requirements:
        - Line 1 must be a valid meta title, 50-60 characters, with the exact main keyword.
        - Line 2 must be a valid meta description, 120-160 characters, with the exact main keyword.
        - The H1 must contain the exact main keyword naturally.
        - The article must contain at least two H2 headings.
        - The exact main keyword must appear once in the introduction, once in a middle section, and once near the conclusion when natural.
        - For article-style content of 350-600 words, the exact main keyword should usually appear about 4 to 6 times naturally.
        - The article should normally be 350-600 words unless the requested platform strongly requires shorter copy.
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        Create marketing content with these inputs:

        - Platform: {platform}
        - Tone: {tone}
        - Main keyword: {keyword}
        - Length: {length_limit}
        - Language: {language}

        Optimize the result to maximize all of these checks:
        - Keyword appears in H1
        - Keyword density between 1% and 3%
        - At least two H2 headings
        - Content length above 300 words
        - Meta title is valid
        - Meta description is valid

        Additional instructions:
        - Use the exact main keyword in the meta title, meta description, and H1.
        - Make the meta title compelling and clickable, not just keyword-heavy.
        - Make the meta description persuasive and natural.
        - The H1 should read like a real headline, not a forced SEO phrase.
        - The H2 headings should be meaningful and editorial.
        - Keep paragraph flow smooth and human.
        - Open with a strong hook paragraph.
        - Keep each paragraph concise and easy to scan.
        - Add one natural Markdown bullet list if the topic allows it.
        - Use Markdown bold to emphasize 2 to 4 important phrases naturally.
        - Avoid repeating the exact keyword unnaturally in every paragraph.
        - After using the exact keyword in strategic locations, use natural related wording elsewhere.
        - Use the exact main keyword once in the introduction, once in a middle section, and once near the ending when natural.
        - If the content is article-style, aim to use the exact main keyword around 4 to 6 times naturally.

        Return the final result in this exact structure only:

        <meta title only>
        <meta description only>

        # **<H1 containing the exact main keyword>**
        <opening paragraph>
        ## **<H2 section 1>**
        <supporting paragraph(s)>
        ## **<H2 section 2>**
        <supporting paragraph(s)>
        <optional CTA>

        Example structure only:
        Best Smart Home Device for Modern Living
        Discover the best smart home device for comfort, security, and convenience with practical tips to upgrade your home more intelligently today.

        # **Best Smart Home Device for Smarter Daily Living**
        ...
        ## **Why the Best Smart Home Device Matters**
        ...
        ## **How to Choose the Best Smart Home Device**
        ...
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

        GeneratedContentParts generatedParts = extractGeneratedContentParts(chatResponse);
        Content savedContent = contentRepository.save(Content.builder()
                .campaign(campaign)
                .user(user)
                .targetKeyword(request.keyword())
                .promptConfig(buildPromptConfig(request))
                .generatedText(generatedParts.body())
                .seoMetadata(generatedParts.seoMetadata())
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

    private GeneratedContentParts extractGeneratedContentParts(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            throw new AppException(ErrorCode.AI_GENERATION_FAILED);
        }

        String content = chatResponse.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new AppException(ErrorCode.AI_GENERATION_FAILED);
        }

        String normalized = content.replace("\r\n", "\n").trim();
        List<String> lines = normalized.lines().toList();
        if (lines.size() < 3) {
            log.warn("AI output could not be split into meta/body. Falling back to raw body only.");
            return new GeneratedContentParts(normalized, null);
        }

        String metaTitle = lines.get(0).trim();
        String metaDescription = lines.get(1).trim();
        int bodyStartIndex = 2;
        while (bodyStartIndex < lines.size() && lines.get(bodyStartIndex).isBlank()) {
            bodyStartIndex++;
        }

        String body = String.join("\n", lines.subList(bodyStartIndex, lines.size())).trim();
        if (metaTitle.isBlank() || metaDescription.isBlank() || body.isBlank()) {
            log.warn("AI output meta/body parsing produced blank sections. Falling back to raw body only.");
            return new GeneratedContentParts(normalized, null);
        }

        return new GeneratedContentParts(
                body,
                new SeoMetadata(
                        0.0,
                        0.0,
                        false,
                        false,
                        0,
                        metaTitle,
                        metaDescription,
                        false,
                        false,
                        List.of()
                )
        );
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
                seoMetadata.hasH2(),
                seoMetadata.wordCount(),
                seoMetadata.metaTitle(),
                seoMetadata.metaDescription(),
                seoMetadata.metaTitleValid(),
                seoMetadata.metaDescriptionValid(),
                seoMetadata.suggestions()
        );
    }

    private record GeneratedContentParts(String body, SeoMetadata seoMetadata) {
    }
}

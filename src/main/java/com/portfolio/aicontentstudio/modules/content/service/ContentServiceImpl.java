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
        You are the backend prompt engine of an AI Content Studio.
        Your job is to transform structured form inputs into publish-ready marketing copy that matches the requested platform, tone, and length.

        Global rules:
        - Return only final content. No explanations, no labels, no JSON/XML, no code fences.
        - Write in the requested language.
        - Follow the platform strategy, tone rules, SEO rules, and output contract exactly.
        - Keep the copy natural, useful, conversion-oriented, and not robotic.
        - Never ramble or drift away from the user's requested platform intent.
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        Build content from this structured form input:
        - Platform: {platform}
        - Tone: {tone}
        - Main keyword: {keyword}
        - Target length: {length_limit}
        - Language: {language}

        Platform strategy:
        {platform_strategy}

        Tone rules:
        {tone_strategy}

        Length guidance:
        {length_guidance}

        SEO and keyword rules:
        {seo_rules}

        Output contract:
        {output_contract}
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
        content.setBannerConfig(request.bannerConfig());

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
        PlatformProfile platformProfile = resolvePlatformProfile(request.platform());
        Map<String, Object> model = Map.of(
                "platform", request.platform(),
                "tone", request.tone(),
                "keyword", request.keyword(),
                "length_limit", request.lengthLimit() != null ? request.lengthLimit() + " words" : "platform-appropriate",
                "language", request.language(),
                "platform_strategy", buildPlatformStrategy(platformProfile, request),
                "tone_strategy", buildToneStrategy(request.tone()),
                "length_guidance", buildLengthGuidance(platformProfile, request.lengthLimit()),
                "seo_rules", buildSeoRules(platformProfile, request.lengthLimit()),
                "output_contract", buildOutputContract(platformProfile)
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

    private PlatformProfile resolvePlatformProfile(String platform) {
        if (platform == null) {
            return PlatformProfile.SEO_LONG_FORM;
        }

        String normalized = platform.trim().toLowerCase();
        return switch (normalized) {
            case "facebook", "facebook page", "instagram", "instagram post" -> PlatformProfile.SOCIAL_POST;
            case "website", "website blog", "blog" -> PlatformProfile.SEO_LONG_FORM;
            case "email", "email marketing" -> PlatformProfile.EMAIL_COPY;
            case "tiktok", "tiktok script" -> PlatformProfile.SHORT_SCRIPT;
            case "google ads" -> PlatformProfile.AD_COPY;
            default -> PlatformProfile.SEO_LONG_FORM;
        };
    }

    private String buildPlatformStrategy(PlatformProfile platformProfile, GenerateContentRequest request) {
        return switch (platformProfile) {
            case SEO_LONG_FORM -> """
                    - Create a blog-style article that is informative, well-structured, and SEO-friendly.
                    - Use exactly one H1 and at least two meaningful H2 headings.
                    - Open with a strong hook and make sure the first paragraph naturally includes the exact main keyword.
                    - Develop clear sections and end with a concise CTA.
                    - Include one natural Markdown bullet list when useful.
                    - Make the copy feel editorial, not generic SEO filler.
                    """;
            case SOCIAL_POST -> """
                    - Create a social-media post suitable for the selected platform.
                    - Lead with a strong hook in the first 1 to 2 lines.
                    - The first sentence of the body must contain the exact main keyword naturally.
                    - Keep the body concise, punchy, and easy to scan on mobile.
                    - Prioritize clarity, audience resonance, scanability, and CTA over long-form structure.
                    - Use at least three short paragraphs or one bullet list so the post is visibly scannable.
                    - End with an explicit CTA such as Learn more, Shop now, Discover more, Contact us, Get started, Xem ngay, Tim hieu, Dang ky, or Mua ngay when natural in the requested language.
                    - Do not force blog-style H1 or H2 headings for this platform.
                    """;
            case EMAIL_COPY -> """
                    - Write marketing copy suitable for an email campaign.
                    - Treat line 1 like a strong subject line and line 2 like preview text.
                    - Make the opening immediately relevant and benefit-led.
                    - The first sentence of the body must contain the exact main keyword naturally.
                    - Keep the body skimmable with at least four short paragraphs or one bullet list.
                    - Use a polished, persuasive structure with a clear CTA near the end.
                    - Do not force blog-style H1 or H2 headings for this platform.
                    """;
            case SHORT_SCRIPT -> """
                    - Write in a spoken, energetic style suitable for a short-form video script.
                    - Treat line 1 like the title or opening hook and line 2 like a supporting teaser.
                    - Make the opening hook fast and attention-grabbing.
                    - The first spoken line of the body must contain the exact main keyword naturally.
                    - Use at least three short spoken beats or one short bullet list so the rhythm is clearly segmented.
                    - Include a clear ending CTA such as Watch now, Learn more, Explore, Get started, Xem ngay, Tim hieu, or Bat dau when natural in the requested language.
                    - Do not force blog-style H1 or H2 headings for this platform.
                    """;
            case AD_COPY -> """
                    - Write conversion-focused copy with high clarity and strong commercial intent.
                    - Treat line 1 like a strong primary headline and line 2 like a supporting ad description.
                    - The first sentence of the body must contain the exact main keyword naturally.
                    - Prioritize value proposition, urgency, specificity, and CTA.
                    - Keep lines tight and avoid unnecessary exposition.
                    - Use a direct CTA such as Learn more, Shop now, Buy now, Contact us, Get started, Xem ngay, Tim hieu, Lien he, or Mua ngay when natural in the requested language.
                    - Use compact body copy that reads like ad copy, not like a blog article.
                    - Do not force blog-style H1 or H2 headings for this platform.
                    """;
        };
    }

    private String buildToneStrategy(String tone) {
        if (tone == null || tone.isBlank()) {
            return """
                    - Keep the tone balanced, clear, and audience-friendly.
                    - Avoid sounding robotic or bland.
                    """;
        }

        return switch (tone.trim().toLowerCase()) {
            case "professional" -> """
                    - Sound credible, polished, and confident.
                    - Prefer clarity and authority over hype.
                    """;
            case "funny" -> """
                    - Add light humor and charm without sounding childish.
                    - Keep the joke density controlled so the message still converts.
                    """;
            case "persuasive" -> """
                    - Be benefit-led, convincing, and action-oriented.
                    - Use urgency or contrast naturally when helpful.
                    """;
            case "friendly" -> """
                    - Sound warm, approachable, and easy to trust.
                    - Keep the wording natural and conversational.
                    """;
            case "creative" -> """
                    - Use fresh phrasing and vivid wording while staying clear.
                    - Aim for memorable but still commercially useful copy.
                    """;
            case "minimalist" -> """
                    - Use concise, clean sentences with minimal fluff.
                    - Keep the message sharp and visually light.
                    """;
            default -> """
                    - Match the requested tone faithfully.
                    - Keep the copy natural and audience-appropriate.
                    """;
        };
    }

    private String buildLengthGuidance(PlatformProfile platformProfile, Integer lengthLimit) {
        if (lengthLimit != null) {
            return "Aim for around " + lengthLimit + " words while keeping the copy natural and appropriately formatted for the platform.";
        }

        return switch (platformProfile) {
            case SEO_LONG_FORM -> "Aim for roughly 450 to 700 words unless the topic strongly needs a different length.";
            case SOCIAL_POST -> "Keep the copy concise and mobile-friendly, usually around 120 to 280 words.";
            case EMAIL_COPY -> "Aim for roughly 180 to 350 words with fast readability.";
            case SHORT_SCRIPT -> "Aim for roughly 120 to 220 words in a spoken rhythm.";
            case AD_COPY -> "Aim for roughly 80 to 180 words with very high signal density.";
        };
    }

    private String buildSeoRules(PlatformProfile platformProfile, Integer lengthLimit) {
        String keywordUsageTarget = buildKeywordUsageTarget(platformProfile, lengthLimit);
        return switch (platformProfile) {
            case SEO_LONG_FORM -> """
                    - Use the exact main keyword in the meta title, meta description, and H1.
                    - Keep exact-keyword density roughly between 1%% and 3%%.
                    - Make the meta title compelling and keep it between 50 and 60 characters.
                    - Make the meta description persuasive and keep it between 120 and 160 characters.
                    - The H1 must contain the exact main keyword naturally.
                    - Include at least two meaningful H2 headings.
                    - Exceed 300 words and make the sections substantial.
                    - Use the exact keyword once in the introduction, once in a middle section, and once near the conclusion when natural.
                    - Keep exact-match keyword usage close to this target: %s
                    - After strategic exact-match placements, use natural related wording elsewhere.
                    """.formatted(keywordUsageTarget);
            case EMAIL_COPY -> """
                    - Treat line 1 as the email subject line and line 2 as preview text.
                    - Include the exact main keyword in the subject line, preview text, and opening email copy.
                    - The first sentence of the body must contain the exact main keyword.
                    - Keep keyword usage natural, usually within this target: %s
                    - Keep keyword density controlled and usually below 3.5%%.
                    - Make the subject line compelling and keep it between 50 and 60 characters.
                    - Make the preview text persuasive and keep it between 120 and 160 characters.
                    - Use sections, bullets, or at least four short paragraph breaks for scanability.
                    - Exceed 180 words.
                    """.formatted(keywordUsageTarget);
            case SOCIAL_POST -> """
                    - Mention the exact main keyword in the first sentence of the body.
                    - Keep keyword usage natural, usually within this target: %s
                    - Use at least three short paragraphs, or a bullet list, to improve scanability.
                    - Exceed 120 words.
                    - Include a clear CTA by the end using an action phrase.
                    - Do not force formal SEO density or blog heading rules if they hurt the natural social flow.
                    """.formatted(keywordUsageTarget);
            case SHORT_SCRIPT -> """
                    - Mention the exact main keyword in the first spoken line of the body.
                    - Keep keyword usage natural, usually within this target: %s
                    - Structure the body as at least three short spoken beats or short script sections.
                    - Exceed 100 words.
                    - Include a clear CTA or audience prompt near the end using an action phrase.
                    - Do not force blog-style SEO heading rules for this platform.
                    """.formatted(keywordUsageTarget);
            case AD_COPY -> """
                    - Mention the exact main keyword in the first sentence of the body.
                    - Keep keyword usage natural, usually within this target: %s
                    - Exceed 80 words while staying concise and commercially sharp.
                    - Include a direct CTA using an action phrase.
                    - Prioritize conversion clarity over blog-style SEO structure.
                    """.formatted(keywordUsageTarget);
        };
    }

    private String buildOutputContract(PlatformProfile platformProfile) {
        return switch (platformProfile) {
            case SEO_LONG_FORM -> """
                    - Return only the final result in this exact structure.
                    - Line 1: meta title only.
                    - Line 2: meta description only.
                    - Line 3: blank line.
                    - Line 4 onward: Markdown article body.
                    - Start the body with exactly one H1 in this format: # **Heading**
                    - Use at least two H2 headings in this format: ## **Heading**
                    - Bold 2 to 4 additional important phrases naturally inside the body.
                    - Do not print labels such as Meta Title, Meta Description, H1, H2, or CTA.
                    """;
            case EMAIL_COPY -> """
                    - Return only the final result in this exact structure.
                    - Line 1: subject line only.
                    - Line 2: preview text only.
                    - Line 3: blank line.
                    - Line 4 onward: email body in clean Markdown-friendly paragraphs or bullets.
                    - Do not print labels such as Subject Line, Preview Text, Body, or CTA.
                    """;
            case SOCIAL_POST -> """
                    - Return only the final result in this exact structure.
                    - Line 1: short headline or hook line only.
                    - Line 2: short supporting teaser line only.
                    - Line 3: blank line.
                    - Line 4 onward: the full social post body with short paragraphs, line breaks, or bullets.
                    - Do not print labels such as Headline, Teaser, Post, or CTA.
                    """;
            case SHORT_SCRIPT -> """
                    - Return only the final result in this exact structure.
                    - Line 1: title or opening hook only.
                    - Line 2: short supporting teaser line only.
                    - Line 3: blank line.
                    - Line 4 onward: script body using short spoken beats and natural line breaks.
                    - Do not print labels such as Hook, Script, Scene, or CTA.
                    """;
            case AD_COPY -> """
                    - Return only the final result in this exact structure.
                    - Line 1: primary headline only.
                    - Line 2: supporting ad description only.
                    - Line 3: blank line.
                    - Line 4 onward: compact ad body copy.
                    - Do not print labels such as Headline, Description, Body, or CTA.
                    """;
        };
    }

    private String buildKeywordUsageTarget(PlatformProfile platformProfile, Integer lengthLimit) {
        if (platformProfile == PlatformProfile.SEO_LONG_FORM) {
            if (lengthLimit == null) {
                return "4 to 6 exact-match mentions";
            }

            if (lengthLimit < 300) {
                return "2 to 3 exact-match mentions";
            }

            if (lengthLimit < 500) {
                return "3 to 4 exact-match mentions";
            }

            if (lengthLimit < 800) {
                return "4 to 6 exact-match mentions";
            }

            return "5 to 7 exact-match mentions";
        }

        if (platformProfile == PlatformProfile.AD_COPY) {
            if (lengthLimit == null || lengthLimit <= 120) {
                return "1 to 2 exact-match mentions";
            }

            return "1 to 3 exact-match mentions";
        }

        if (platformProfile == PlatformProfile.EMAIL_COPY) {
            if (lengthLimit == null || lengthLimit <= 220) {
                return "1 to 3 exact-match mentions";
            }

            return "2 to 4 exact-match mentions";
        }

        if (lengthLimit == null || lengthLimit <= 180) {
            return "1 to 2 exact-match mentions";
        }

        if (lengthLimit <= 320) {
            return "2 to 3 exact-match mentions";
        }

        return "3 to 4 exact-match mentions";
    }

    private record GeneratedContentParts(String body, SeoMetadata seoMetadata) {
    }

    private enum PlatformProfile {
        SEO_LONG_FORM,
        SOCIAL_POST,
        EMAIL_COPY,
        SHORT_SCRIPT,
        AD_COPY
    }
}

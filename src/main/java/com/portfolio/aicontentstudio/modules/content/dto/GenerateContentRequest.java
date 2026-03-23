package com.portfolio.aicontentstudio.modules.content.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for AI content generation via SSE streaming.
 * All fields undergo @Validated check before the expensive AI call.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GenerateContentRequest(

        @NotNull(message = "campaign_id is required")
        UUID campaignId,

        @NotBlank(message = "platform is required (e.g. Facebook, Website, Email)")
        String platform,

        @NotBlank(message = "tone is required (e.g. Professional, Friendly, Humorous)")
        String tone,

        @NotBlank(message = "keyword is required for SEO optimization")
        String keyword,

        // Optional: suggest approximate output length to guide prompt
        Integer lengthLimit,

        @NotBlank(message = "language is required (e.g. Vietnamese, English)")
        String language
) {}

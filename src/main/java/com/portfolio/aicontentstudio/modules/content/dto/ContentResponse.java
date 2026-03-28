package com.portfolio.aicontentstudio.modules.content.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for content data returned to API clients.
 * snake_case JSON naming via @JsonNaming.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ContentResponse(
        UUID id,
        UUID campaignId,
        String campaignName,
        String targetKeyword,
        PromptConfig promptConfig,
        String generatedText,
        SeoMetadata seoMetadata,
        String bannerUrl,
        BannerConfig bannerConfig,
        ContentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

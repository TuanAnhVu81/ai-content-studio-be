package com.portfolio.aicontentstudio.modules.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminRecentContentResponse(
        UUID id,
        UUID campaignId,
        String campaignName,
        UUID userId,
        String ownerEmail,
        String targetKeyword,
        String contentPreview,
        String bannerUrl,
        ContentStatus status,
        LocalDateTime createdAt
) {
}

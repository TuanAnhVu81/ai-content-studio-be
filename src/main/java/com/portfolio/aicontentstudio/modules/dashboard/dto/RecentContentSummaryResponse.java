package com.portfolio.aicontentstudio.modules.dashboard.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecentContentSummaryResponse(
        UUID id,
        UUID campaignId,
        String campaignName,
        String targetKeyword,
        ContentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

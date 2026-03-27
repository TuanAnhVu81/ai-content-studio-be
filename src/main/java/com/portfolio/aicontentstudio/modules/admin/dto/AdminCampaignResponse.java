package com.portfolio.aicontentstudio.modules.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.campaign.dto.CampaignMetadata;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminCampaignResponse(
        UUID id,
        String name,
        CampaignStatus status,
        CampaignMetadata metadata,
        UUID userId,
        String ownerEmail,
        long contentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

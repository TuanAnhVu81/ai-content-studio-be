package com.portfolio.aicontentstudio.modules.campaign.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe response DTO for Campaign - never exposes the entity directly.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CampaignResponse(
        UUID id,
        String name,
        CampaignStatus status,
        CampaignMetadata metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

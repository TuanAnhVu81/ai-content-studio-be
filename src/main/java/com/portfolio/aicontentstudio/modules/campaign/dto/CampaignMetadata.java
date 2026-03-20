package com.portfolio.aicontentstudio.modules.campaign.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Metadata stored in campaigns.metadata JSONB column.
 * Uses snake_case for JSON properties (e.g. target_audience).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CampaignMetadata(
        String goal,
        String targetAudience
) {
}

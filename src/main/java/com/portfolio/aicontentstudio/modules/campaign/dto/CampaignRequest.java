package com.portfolio.aicontentstudio.modules.campaign.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.campaign.entity.CampaignStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a Campaign.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CampaignRequest(

        @NotBlank(message = "Campaign name must not be blank")
        @Size(min = 3, max = 100, message = "Campaign name must be between 3 and 100 characters")
        String name,

        @NotNull(message = "Campaign status must not be null")
        CampaignStatus status,

        @Valid
        CampaignMetadata metadata
) {
}

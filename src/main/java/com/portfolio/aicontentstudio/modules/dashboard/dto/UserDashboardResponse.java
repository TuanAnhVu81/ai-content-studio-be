package com.portfolio.aicontentstudio.modules.dashboard.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserDashboardResponse(
        long totalCampaigns,
        long totalContents,
        long totalTokensUsed30Days,
        List<ContentResponse> recentContents
) {
}

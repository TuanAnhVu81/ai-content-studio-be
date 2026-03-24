package com.portfolio.aicontentstudio.modules.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiUsageStatsResponse(
        LocalDateTime from,
        LocalDateTime to,
        Long totalPromptTokens,
        Long totalResponseTokens,
        Long totalTokens,
        List<ModelUsageResponse> tokensByModel
) {
}

package com.portfolio.aicontentstudio.modules.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TopUserUsageResponse(
        UUID userId,
        String email,
        String fullName,
        Long totalTokens,
        Long promptTokens,
        Long responseTokens
) {
}

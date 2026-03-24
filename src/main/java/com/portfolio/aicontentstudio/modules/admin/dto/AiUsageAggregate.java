package com.portfolio.aicontentstudio.modules.admin.dto;

public record AiUsageAggregate(
        Long totalPromptTokens,
        Long totalResponseTokens,
        Long totalTokens
) {
}

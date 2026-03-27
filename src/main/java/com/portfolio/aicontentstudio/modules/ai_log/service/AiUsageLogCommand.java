package com.portfolio.aicontentstudio.modules.ai_log.service;

import java.util.UUID;

record AiUsageLogCommand(
        UUID userId,
        UUID contentId,
        Integer promptTokens,
        Integer responseTokens,
        Integer totalTokens,
        String modelName
) {
}

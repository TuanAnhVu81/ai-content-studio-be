package com.portfolio.aicontentstudio.modules.health.dto;

import java.time.LocalDateTime;

/**
 * Response payload for liveness checks.
 */
public record HealthLiveResponse(
        String status,
        String service,
        String environment,
        LocalDateTime timestamp
) {
}

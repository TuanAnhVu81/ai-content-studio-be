package com.portfolio.aicontentstudio.modules.health.dto;

import java.time.LocalDateTime;

/**
 * Response payload for readiness checks.
 */
public record HealthReadyResponse(
        String status,
        String service,
        String environment,
        LocalDateTime timestamp,
        HealthCheckStatusResponse checks
) {
}

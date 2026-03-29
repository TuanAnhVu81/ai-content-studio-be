package com.portfolio.aicontentstudio.modules.health.dto;

/**
 * Lightweight dependency status for readiness checks.
 */
public record HealthCheckStatusResponse(
        String database,
        String redis
) {
}

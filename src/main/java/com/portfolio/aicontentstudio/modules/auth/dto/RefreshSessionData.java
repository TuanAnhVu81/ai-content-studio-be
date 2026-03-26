package com.portfolio.aicontentstudio.modules.auth.dto;

import java.util.UUID;

public record RefreshSessionData(
        UUID sessionId,
        UUID userId,
        String currentTokenHash,
        long createdAtEpochMs,
        long expiresAtEpochMs,
        long lastRotatedAtEpochMs,
        String ipAddress,
        String userAgent,
        String status,
        Long revokedAtEpochMs
) {
}

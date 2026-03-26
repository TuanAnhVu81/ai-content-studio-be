package com.portfolio.aicontentstudio.modules.auth.dto;

import java.util.UUID;

public record RefreshSessionResult(
        UUID sessionId,
        UUID userId,
        String refreshToken
) {
}

package com.portfolio.aicontentstudio.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for refreshing the Access Token.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}

package com.portfolio.aicontentstudio.modules.auth.dto;

/**
 * Response payload after a successful login or token refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public AuthResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer");
    }
}

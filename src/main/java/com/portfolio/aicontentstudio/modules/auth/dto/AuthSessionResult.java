package com.portfolio.aicontentstudio.modules.auth.dto;

public record AuthSessionResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserResponse user
) {
    public AuthSessionResult(String accessToken, String refreshToken, UserResponse user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}

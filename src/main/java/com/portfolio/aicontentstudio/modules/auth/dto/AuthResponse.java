package com.portfolio.aicontentstudio.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response payload after a successful login or token refresh.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthResponse(
        String accessToken,
        String tokenType,
        UserResponse user
) {
    public AuthResponse(String accessToken, UserResponse user) {
        this(accessToken, "Bearer", user);
    }
}

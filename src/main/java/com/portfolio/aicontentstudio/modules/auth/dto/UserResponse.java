package com.portfolio.aicontentstudio.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;

import java.util.Set;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        AccountStatus status,
        Set<String> roles
) {
}

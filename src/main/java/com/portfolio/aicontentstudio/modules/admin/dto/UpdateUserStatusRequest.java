package com.portfolio.aicontentstudio.modules.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateUserStatusRequest(
        @NotNull(message = "status is required")
        AccountStatus status,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {
}

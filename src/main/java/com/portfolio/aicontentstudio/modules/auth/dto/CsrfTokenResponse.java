package com.portfolio.aicontentstudio.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {
}

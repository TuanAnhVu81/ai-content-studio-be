package com.portfolio.aicontentstudio.modules.content.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BannerConfig(

        @NotBlank(message = "format cannot be blank")
        @Size(max = 30, message = "format must not exceed 30 characters")
        String format,

        @NotBlank(message = "template_key cannot be blank")
        @Size(max = 80, message = "template_key must not exceed 80 characters")
        String templateKey,

        @NotBlank(message = "headline cannot be blank")
        @Size(max = 180, message = "headline must not exceed 180 characters")
        String headline,

        @Size(max = 240, message = "subtext must not exceed 240 characters")
        String subtext,

        @Size(max = 80, message = "cta must not exceed 80 characters")
        String cta
) {}

package com.portfolio.aicontentstudio.modules.content.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for persisting manually edited content + SEO metadata.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateContentRequest(

        @NotBlank(message = "generated_text cannot be blank")
        String generatedText,

        // Optional SEO analysis result persisted from Frontend's real-time analyzer
        @Valid
        SeoMetadata seoMetadata
) {}

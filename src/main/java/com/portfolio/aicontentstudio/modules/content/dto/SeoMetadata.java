package com.portfolio.aicontentstudio.modules.content.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.io.Serializable;

/**
 * Metadata for SEO scoring, stored as JSONB.
 */
public record SeoMetadata(
    @Min(0) @Max(100) double score,
    @DecimalMin("0.0") @DecimalMax("100.0") double keywordDensity,
    boolean hasH1,
    String suggestion
) implements Serializable {}

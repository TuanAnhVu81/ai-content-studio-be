package com.portfolio.aicontentstudio.modules.content.dto;

import java.io.Serializable;

/**
 * Metadata for SEO scoring, stored as JSONB.
 */
public record SeoMetadata(
    double score,
    double keywordDensity,
    boolean hasH1,
    String suggestion
) implements Serializable {}

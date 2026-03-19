package com.portfolio.aicontentstudio.modules.content.dto;

import java.io.Serializable;

/**
 * Configuration record for AI prompts, stored as JSONB.
 */
public record PromptConfig(
    String platform,
    String tone,
    String length,
    String language
) implements Serializable {}

package com.yipeng.jobcopilot.dto;

/**
 * A single resume bullet point rewrite suggestion.
 */
public record BulletSuggestion(
        String original,
        String improved,
        String reason
) {}
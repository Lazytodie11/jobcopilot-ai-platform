package com.yipeng.jobcopilot.dto;

import java.util.List;

/**
 * Structured result returned by AiSkillExtractorService.
 *
 * requiredSkills  — skills explicitly required by the JD
 * preferredSkills — skills listed as "nice to have", "preferred", or as alternatives (e.g. "Go or Java")
 *
 * Only requiredSkills affect the match score.
 * preferredSkills are shown separately so the user knows what to aim for next.
 */
public record AiSkillExtractionResult(
        List<String> requiredSkills,
        List<String> preferredSkills
) {}
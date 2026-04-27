package com.yipeng.jobcopilot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ResumeSuggestionResponse {

    private Long resumeId;
    private Long jobPostId;

    /** High-level strategy for positioning this resume toward this JD. */
    private String overallStrategy;

    /**
     * Concrete bullet point rewrites.
     * Each entry contains the original text, an improved version, and the reason for the change.
     */
    private List<BulletSuggestion> bulletSuggestions;

    /** Skills already in the resume that should be made more prominent. */
    private List<String> skillsToHighlight;

    /**
     * Top skills worth acquiring to improve fit for this type of role.
     * Capped at 3 — more than that is overwhelming.
     */
    private List<String> skillsToAcquire;

    /** Additional tips that don't fit the bullet rewrite format. */
    private List<String> additionalTips;
}
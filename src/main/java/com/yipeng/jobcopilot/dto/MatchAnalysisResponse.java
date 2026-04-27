package com.yipeng.jobcopilot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MatchAnalysisResponse {

    private Long resumeId;
    private Long jobPostId;

    private int matchScore;
    private int weightedMatchScore;

    /** Required skills found in the resume. */
    private List<String> matchedSkills;

    /** Required skills missing from the resume. */
    private List<String> missingSkills;

    /** Subset of missingSkills that are CORE tier. */
    private List<String> coreMissingSkills;

    /**
     * Preferred/alternative skills found in the resume.
     * Shown when the JD has no hard required skills (e.g. "any of Go, Python, Java").
     */
    private List<String> preferredSkillsMatched;

    /** Preferred/alternative skills missing from the resume. */
    private List<String> preferredSkillsMissing;

    /** True = AI extraction used. False = keyword fallback. */
    private boolean aiExtractionUsed;

    private String summary;
}
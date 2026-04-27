package com.yipeng.jobcopilot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiMatchAnalysisResponse {

    private Long resumeId;
    private Long jobPostId;

    private int matchScore;
    private String aiSummary;

    private Boolean fallbackUsed;
    private String fallbackReason;

    private List<String> matchedSkills;
    private List<String> missingSkills;

    private String rawAiResponse;
}
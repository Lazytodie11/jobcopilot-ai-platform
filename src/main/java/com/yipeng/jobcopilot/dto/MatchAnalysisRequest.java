package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotNull;

public class MatchAnalysisRequest {

    @NotNull(message = "Resume id is required")
    private Long resumeId;

    @NotNull(message = "Job post id is required")
    private Long jobPostId;

    public Long getResumeId() {
        return resumeId;
    }

    public Long getJobPostId() {
        return jobPostId;
    }
}
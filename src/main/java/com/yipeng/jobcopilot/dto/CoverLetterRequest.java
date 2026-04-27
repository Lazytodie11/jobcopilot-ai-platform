package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoverLetterRequest {

    @NotNull(message = "resumeId is required")
    private Long resumeId;

    @NotNull(message = "jobPostId is required")
    private Long jobPostId;

    // Optional: user can provide extra context, e.g. "emphasize my ML experience"
    private String extraContext;
}
package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelfIntroRequest {

    @NotNull(message = "resumeId is required")
    private Long resumeId;

    @NotNull(message = "jobPostId is required")
    private Long jobPostId;
}
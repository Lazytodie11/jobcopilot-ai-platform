package com.yipeng.jobcopilot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MatchResultResponse {

    private Long id;

    private Long resumeId;
    private String resumeTitle;

    private Long jobPostId;
    private String companyName;
    private String jobTitle;

    private int matchScore;
    private String aiSummary;
    private boolean fallbackUsed;

    private LocalDateTime createdAt;
}
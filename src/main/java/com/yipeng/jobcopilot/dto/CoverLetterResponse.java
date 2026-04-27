package com.yipeng.jobcopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CoverLetterResponse {

    private Long resumeId;
    private Long jobPostId;
    private String companyName;
    private String jobTitle;
    private String coverLetter;
    private LocalDateTime generatedAt;
}
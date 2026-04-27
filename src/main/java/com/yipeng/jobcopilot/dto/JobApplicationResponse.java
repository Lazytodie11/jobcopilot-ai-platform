package com.yipeng.jobcopilot.dto;

import com.yipeng.jobcopilot.enumeration.ApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class JobApplicationResponse {

    private Long id;
    private String companyName;
    private String jobTitle;
    private String jobUrl;
    private ApplicationStatus status;
    private LocalDate appliedDate;
    private String notes;
    private Long userId;
    private Long resumeId;
    private String resumeTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
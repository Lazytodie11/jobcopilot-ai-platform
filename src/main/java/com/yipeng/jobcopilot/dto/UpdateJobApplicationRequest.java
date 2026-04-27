package com.yipeng.jobcopilot.dto;

import com.yipeng.jobcopilot.enumeration.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class UpdateJobApplicationRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    private String jobUrl;

    private ApplicationStatus status;

    private LocalDate appliedDate;

    private String notes;

    private Long resumeId;

    public String getCompanyName() {
        return companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public String getNotes() {
        return notes;
    }

    public Long getResumeId() {
        return resumeId;
    }
}
package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateJobPostRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    private String jobUrl;
    private String location;
    private String employmentType;
    private String source;
    private String description;

    public String getCompanyName() {
        return companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public String getLocation() {
        return location;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }
}
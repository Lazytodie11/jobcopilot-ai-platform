package com.yipeng.jobcopilot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class JobPostResponse {

    private Long id;
    private String companyName;
    private String jobTitle;
    private String jobUrl;
    private String location;
    private String employmentType;
    private String source;
    private String description;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
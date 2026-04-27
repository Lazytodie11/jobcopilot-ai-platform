package com.yipeng.jobcopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MockInterviewResponse {

    private Long resumeId;
    private Long jobPostId;
    private String companyName;
    private String jobTitle;
    private List<MockInterviewQuestion> questions;
    private LocalDateTime generatedAt;
}
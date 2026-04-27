package com.yipeng.jobcopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class JobPostRecommendationResponse {

    private Long resumeId;
    private String resumeTitle;
    private List<RankedJobPost> recommendedJobPosts;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RankedJobPost {
        private Long jobPostId;
        private String companyName;
        private String jobTitle;
        private double similarityScore;  // 0.0 - 1.0
        private String matchReason;      // 一句话解释为什么推荐
    }
}
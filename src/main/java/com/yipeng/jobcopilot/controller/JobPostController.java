package com.yipeng.jobcopilot.controller;

import com.yipeng.jobcopilot.dto.ApiResponse;
import com.yipeng.jobcopilot.dto.CreateJobPostRequest;
import com.yipeng.jobcopilot.dto.JobPostRecommendationResponse;
import com.yipeng.jobcopilot.dto.JobPostResponse;
import com.yipeng.jobcopilot.dto.ParseJobUrlRequest;
import com.yipeng.jobcopilot.dto.ParsedJobPostResponse;
import com.yipeng.jobcopilot.dto.UpdateJobPostRequest;
import com.yipeng.jobcopilot.service.JobPostEmbeddingService;
import com.yipeng.jobcopilot.service.JobPostRecommendationService;
import com.yipeng.jobcopilot.service.JobPostService;
import com.yipeng.jobcopilot.service.JobUrlParserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/job-posts")
@RequiredArgsConstructor
public class JobPostController {

    private final JobPostService jobPostService;
    private final JobUrlParserService jobUrlParserService;
    private final JobPostEmbeddingService jobPostEmbeddingService;
    private final JobPostRecommendationService jobPostRecommendationService;

    @PostMapping("/me")
    public ApiResponse<JobPostResponse> createMyJobPost(
            Authentication authentication,
            @Valid @RequestBody CreateJobPostRequest request
    ) {
        String email = authentication.getName();
        JobPostResponse response = jobPostService.createMyJobPost(email, request);
        return ApiResponse.success("Job post created successfully", response);
    }

    @PostMapping("/me/parse-url")
    public ApiResponse<JobPostResponse> createMyJobPostFromUrl(
            Authentication authentication,
            @Valid @RequestBody ParseJobUrlRequest request
    ) {
        String email = authentication.getName();
        ParsedJobPostResponse parsed = jobUrlParserService.parseJobUrl(request.getUrl());
        JobPostResponse response = jobPostService.createMyJobPostFromParsedUrl(email, parsed);
        return ApiResponse.success("Job post parsed and created successfully", response);
    }

    @PostMapping("/parse-url")
    public ApiResponse<ParsedJobPostResponse> parseJobUrl(
            @Valid @RequestBody ParseJobUrlRequest request
    ) {
        ParsedJobPostResponse response = jobUrlParserService.parseJobUrl(request.getUrl());
        return ApiResponse.success("Job URL parsed successfully", response);
    }

    @PostMapping("/me/{id}/embed")
    public ApiResponse<Object> embedExistingJobPost(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String email = authentication.getName();
        JobPostResponse jobPost = jobPostService.getMyJobPostById(email, id);

        if (jobPost.getDescription() == null || jobPost.getDescription().isBlank()) {
            return ApiResponse.failure("Job post description is empty — nothing to embed");
        }

        jobPostEmbeddingService.embedJobPost(
                jobPost.getId(), jobPost.getUserId(), jobPost.getDescription());

        log.info("Re-embedded jobPostId={} for user={}", id, email);
        return ApiResponse.success("Job post embedded successfully", null);
    }

    /**
     * Returns the user's saved job posts ranked by semantic similarity to a given resume.
     *
     * Postman:
     *   GET /api/job-posts/me/recommended?resumeId=7
     *   Authorization: Bearer <token>
     */
    @GetMapping("/me/recommended")
    public ApiResponse<JobPostRecommendationResponse> getRecommendedJobPosts(
            Authentication authentication,
            @RequestParam Long resumeId
    ) {
        String email = authentication.getName();
        JobPostRecommendationResponse response =
                jobPostRecommendationService.recommend(email, resumeId);
        return ApiResponse.success("Job post recommendations generated successfully", response);
    }

    @GetMapping("/me")
    public ApiResponse<List<JobPostResponse>> getMyJobPosts(Authentication authentication) {
        String email = authentication.getName();
        List<JobPostResponse> response = jobPostService.getMyJobPosts(email);
        return ApiResponse.success("Job posts fetched successfully", response);
    }

    @GetMapping("/me/{id}")
    public ApiResponse<JobPostResponse> getMyJobPostById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String email = authentication.getName();
        JobPostResponse response = jobPostService.getMyJobPostById(email, id);
        return ApiResponse.success("Job post fetched successfully", response);
    }

    @PutMapping("/me/{id}")
    public ApiResponse<JobPostResponse> updateMyJobPost(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobPostRequest request
    ) {
        String email = authentication.getName();
        JobPostResponse response = jobPostService.updateMyJobPost(email, id, request);
        return ApiResponse.success("Job post updated successfully", response);
    }

    @DeleteMapping("/me/{id}")
    public ApiResponse<Object> deleteMyJobPost(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String email = authentication.getName();
        jobPostService.deleteMyJobPost(email, id);
        return ApiResponse.success("Job post deleted successfully", null);
    }

    @GetMapping("/me/search/company")
    public ApiResponse<List<JobPostResponse>> searchMyJobPostsByCompany(
            Authentication authentication,
            @RequestParam String companyName
    ) {
        String email = authentication.getName();
        List<JobPostResponse> response =
                jobPostService.searchMyJobPostsByCompany(email, companyName);
        return ApiResponse.success("Job posts searched successfully by company", response);
    }

    @GetMapping("/me/search/title")
    public ApiResponse<List<JobPostResponse>> searchMyJobPostsByTitle(
            Authentication authentication,
            @RequestParam String jobTitle
    ) {
        String email = authentication.getName();
        List<JobPostResponse> response =
                jobPostService.searchMyJobPostsByTitle(email, jobTitle);
        return ApiResponse.success("Job posts searched successfully by title", response);
    }
}
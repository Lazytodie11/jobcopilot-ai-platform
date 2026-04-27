package com.yipeng.jobcopilot.controller;

import com.yipeng.jobcopilot.dto.ApiResponse;
import com.yipeng.jobcopilot.dto.CreateJobApplicationRequest;
import com.yipeng.jobcopilot.dto.JobApplicationResponse;
import com.yipeng.jobcopilot.dto.UpdateJobApplicationRequest;
import com.yipeng.jobcopilot.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping("/me")
    public ApiResponse<List<JobApplicationResponse>> getMyApplications(Authentication authentication) {
        String email = authentication.getName();

        List<JobApplicationResponse> applications = jobApplicationService.getMyApplications(email);
        return ApiResponse.success("Applications fetched successfully", applications);
    }

    @PostMapping("/me")
    public ApiResponse<JobApplicationResponse> createMyApplication(
            Authentication authentication,
            @Valid @RequestBody CreateJobApplicationRequest request
    ) {
        String email = authentication.getName();

        JobApplicationResponse application = jobApplicationService.createMyApplication(email, request);
        return ApiResponse.success("Application created successfully", application);
    }

    @GetMapping("/me/{id}")
    public ApiResponse<JobApplicationResponse> getMyApplicationById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String email = authentication.getName();

        JobApplicationResponse application = jobApplicationService.getMyApplicationById(email, id);
        return ApiResponse.success("Application fetched successfully", application);
    }

    @PutMapping("/me/{id}")
    public ApiResponse<JobApplicationResponse> updateMyApplication(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobApplicationRequest request
    ) {
        String email = authentication.getName();

        JobApplicationResponse application = jobApplicationService.updateMyApplication(email, id, request);
        return ApiResponse.success("Application updated successfully", application);
    }

    @DeleteMapping("/me/{id}")
    public ApiResponse<Object> deleteMyApplication(
            Authentication authentication,
            @PathVariable Long id
    ) {
        String email = authentication.getName();

        jobApplicationService.deleteMyApplication(email, id);
        return ApiResponse.success("Application deleted successfully", null);
    }
}
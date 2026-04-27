package com.yipeng.jobcopilot.controller;

import com.yipeng.jobcopilot.dto.ApiResponse;
import com.yipeng.jobcopilot.dto.CreateResumeRequest;
import com.yipeng.jobcopilot.dto.CreateUserRequest;
import com.yipeng.jobcopilot.dto.LoginRequest;
import com.yipeng.jobcopilot.dto.LoginResponse;
import com.yipeng.jobcopilot.dto.ResumeResponse;
import com.yipeng.jobcopilot.dto.UpdateResumeRequest;
import com.yipeng.jobcopilot.dto.UpdateUserRequest;
import com.yipeng.jobcopilot.dto.UserResponse;
import com.yipeng.jobcopilot.exception.InvalidCredentialsException;
import com.yipeng.jobcopilot.service.ResumeService;
import com.yipeng.jobcopilot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ResumeService resumeService;

    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse createdUser = userService.createUser(request);
        return ApiResponse.success("User created successfully", createdUser);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse loginResponse = userService.login(request);
        return ApiResponse.success("Login successful", loginResponse);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(Authentication authentication) {
        String email = getAuthenticatedEmail(authentication);
        UserResponse currentUser = userService.getUserByEmail(email);
        return ApiResponse.success("Current user fetched successfully", currentUser);
    }

    @GetMapping("/me/resumes")
    public ApiResponse<List<ResumeResponse>> getMyResumes(Authentication authentication) {
        String email = getAuthenticatedEmail(authentication);
        List<ResumeResponse> resumes = resumeService.getMyResumes(email);
        return ApiResponse.success("Current user's resumes fetched successfully", resumes);
    }

    @PostMapping("/me/resumes")
    public ApiResponse<ResumeResponse> createMyResume(
            Authentication authentication,
            @Valid @RequestBody CreateResumeRequest request
    ) {
        String email = getAuthenticatedEmail(authentication);
        ResumeResponse createdResume = resumeService.createMyResume(email, request);
        return ApiResponse.success("Resume created successfully for current user", createdResume);
    }

    @GetMapping("/me/resumes/{resumeId}")
    public ApiResponse<ResumeResponse> getMyResumeById(
            Authentication authentication,
            @PathVariable Long resumeId
    ) {
        String email = getAuthenticatedEmail(authentication);
        ResumeResponse resume = resumeService.getMyResumeById(email, resumeId);
        return ApiResponse.success("Current user's resume fetched successfully", resume);
    }

    @PutMapping("/me/resumes/{resumeId}")
    public ApiResponse<ResumeResponse> updateMyResume(
            Authentication authentication,
            @PathVariable Long resumeId,
            @Valid @RequestBody UpdateResumeRequest request
    ) {
        String email = getAuthenticatedEmail(authentication);
        ResumeResponse updatedResume = resumeService.updateMyResume(email, resumeId, request);
        return ApiResponse.success("Current user's resume updated successfully", updatedResume);
    }

    @DeleteMapping("/me/resumes/{resumeId}")
    public ApiResponse<Object> deleteMyResume(
            Authentication authentication,
            @PathVariable Long resumeId
    ) {
        String email = getAuthenticatedEmail(authentication);
        resumeService.deleteMyResume(email, resumeId);
        return ApiResponse.success("Current user's resume deleted successfully", null);
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ApiResponse.success("Users fetched successfully", users);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ApiResponse.success("User fetched successfully", user);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserResponse updatedUser = userService.updateUser(id, request);
        return ApiResponse.success("User updated successfully", updatedUser);
    }

    @GetMapping("/{userId}/resumes")
    public ApiResponse<List<ResumeResponse>> getResumesByUserId(@PathVariable Long userId) {
        List<ResumeResponse> resumes = resumeService.getResumesByUserId(userId);
        return ApiResponse.success("Resumes fetched successfully for user", resumes);
    }

    @GetMapping("/{userId}/resumes/{resumeId}")
    public ApiResponse<ResumeResponse> getResumeByUserIdAndResumeId(
            @PathVariable Long userId,
            @PathVariable Long resumeId
    ) {
        ResumeResponse resume = resumeService.getResumeByUserIdAndResumeId(userId, resumeId);
        return ApiResponse.success("Resume fetched successfully for user", resume);
    }

    @PutMapping("/{userId}/resumes/{resumeId}")
    public ApiResponse<ResumeResponse> updateResumeByUserIdAndResumeId(
            @PathVariable Long userId,
            @PathVariable Long resumeId,
            @Valid @RequestBody UpdateResumeRequest request
    ) {
        ResumeResponse updatedResume = resumeService.updateResumeByUserIdAndResumeId(userId, resumeId, request);
        return ApiResponse.success("Resume updated successfully for user", updatedResume);
    }

    @DeleteMapping("/{userId}/resumes/{resumeId}")
    public ApiResponse<Object> deleteResumeByUserIdAndResumeId(
            @PathVariable Long userId,
            @PathVariable Long resumeId
    ) {
        resumeService.deleteResumeByUserIdAndResumeId(userId, resumeId);
        return ApiResponse.success("Resume deleted successfully for user", null);
    }

    private String getAuthenticatedEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Unauthorized");
        }

        return authentication.getName();
    }
}
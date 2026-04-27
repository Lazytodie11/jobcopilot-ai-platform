package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.CreateJobApplicationRequest;
import com.yipeng.jobcopilot.dto.JobApplicationResponse;
import com.yipeng.jobcopilot.dto.UpdateJobApplicationRequest;
import com.yipeng.jobcopilot.entity.JobApplication;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.enumeration.ApplicationStatus;
import com.yipeng.jobcopilot.exception.JobApplicationNotFoundException;
import com.yipeng.jobcopilot.exception.ResumeNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.JobApplicationRepository;
import com.yipeng.jobcopilot.repository.ResumeRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;

    // =========================
    // Create
    // =========================

    public JobApplicationResponse createMyApplication(String email, CreateJobApplicationRequest request) {
        User user = getUserByEmail(email);

        Resume resume = null;
        if (request.getResumeId() != null) {
            resume = getResumeOwnedByUser(request.getResumeId(), user.getId());
        }

        JobApplication application = JobApplication.builder()
                .companyName(request.getCompanyName())
                .jobTitle(request.getJobTitle())
                .jobUrl(request.getJobUrl())
                .status(request.getStatus() != null ? request.getStatus() : ApplicationStatus.APPLIED)
                .appliedDate(request.getAppliedDate())
                .notes(request.getNotes())
                .user(user)
                .resume(resume)
                .build();

        return toResponse(jobApplicationRepository.save(application));
    }

    // =========================
    // Read
    // =========================

    public List<JobApplicationResponse> getMyApplications(String email) {
        User user = getUserByEmail(email);

        return jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public JobApplicationResponse getMyApplicationById(String email, Long id) {
        User user = getUserByEmail(email);

        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobApplicationNotFoundException("Application not found"));

        return toResponse(application);
    }

    // =========================
    // Update
    // =========================

    public JobApplicationResponse updateMyApplication(
            String email,
            Long id,
            UpdateJobApplicationRequest request
    ) {
        User user = getUserByEmail(email);

        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobApplicationNotFoundException("Application not found"));

        Resume resume = null;
        if (request.getResumeId() != null) {
            resume = getResumeOwnedByUser(request.getResumeId(), user.getId());
        }

        application.setCompanyName(request.getCompanyName());
        application.setJobTitle(request.getJobTitle());
        application.setJobUrl(request.getJobUrl());
        application.setStatus(request.getStatus());
        application.setAppliedDate(request.getAppliedDate());
        application.setNotes(request.getNotes());
        application.setResume(resume);

        return toResponse(jobApplicationRepository.save(application));
    }

    // =========================
    // Delete
    // =========================

    public void deleteMyApplication(String email, Long id) {
        User user = getUserByEmail(email);

        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobApplicationNotFoundException("Application not found"));

        jobApplicationRepository.delete(application);
    }

    // =========================
    // Helpers
    // =========================

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private Resume getResumeOwnedByUser(Long resumeId, Long userId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found or not owned by user"));
    }

    private JobApplicationResponse toResponse(JobApplication app) {
        return JobApplicationResponse.builder()
                .id(app.getId())
                .companyName(app.getCompanyName())
                .jobTitle(app.getJobTitle())
                .jobUrl(app.getJobUrl())
                .status(app.getStatus())
                .appliedDate(app.getAppliedDate())
                .notes(app.getNotes())
                .userId(app.getUser().getId())
                .resumeId(app.getResume() != null ? app.getResume().getId() : null)
                .resumeTitle(app.getResume() != null ? app.getResume().getTitle() : null)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
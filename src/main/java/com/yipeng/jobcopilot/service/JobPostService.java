package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.CreateJobPostRequest;
import com.yipeng.jobcopilot.dto.JobPostResponse;
import com.yipeng.jobcopilot.dto.ParsedJobPostResponse;
import com.yipeng.jobcopilot.dto.UpdateJobPostRequest;
import com.yipeng.jobcopilot.entity.JobPost;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.exception.JobPostNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.JobPostRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JobPostService {

    private final JobPostRepository jobPostRepository;
    private final UserRepository userRepository;
    private final JobPostEmbeddingService jobPostEmbeddingService;
    private final EmbeddingEventProducer embeddingEventProducer;

    public JobPostResponse createMyJobPost(String email, CreateJobPostRequest request) {
        User user = getUserByEmail(email);

        JobPost jobPost = JobPost.builder()
                .companyName(request.getCompanyName())
                .jobTitle(request.getJobTitle())
                .jobUrl(request.getJobUrl())
                .location(request.getLocation())
                .employmentType(request.getEmploymentType())
                .source(request.getSource())
                .description(request.getDescription())
                .user(user)
                .build();

        JobPost saved = jobPostRepository.save(jobPost);
        publishEmbedEvent(saved);
        return toResponse(saved);
    }

    public JobPostResponse createMyJobPostFromParsedUrl(String email, ParsedJobPostResponse parsed) {
        User user = getUserByEmail(email);

        JobPost jobPost = JobPost.builder()
                .companyName(parsed.getCompanyName())
                .jobTitle(parsed.getJobTitle())
                .jobUrl(parsed.getUrl())
                .location(null)
                .employmentType(null)
                .source("Parsed URL")
                .description(parsed.getDescription())
                .user(user)
                .build();

        JobPost saved = jobPostRepository.save(jobPost);
        publishEmbedEvent(saved);
        return toResponse(saved);
    }

    public List<JobPostResponse> getMyJobPosts(String email) {
        User user = getUserByEmail(email);
        return jobPostRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    public JobPostResponse getMyJobPostById(String email, Long id) {
        User user = getUserByEmail(email);
        JobPost jobPost = jobPostRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));
        return toResponse(jobPost);
    }

    public JobPostResponse updateMyJobPost(String email, Long id, UpdateJobPostRequest request) {
        User user = getUserByEmail(email);

        JobPost jobPost = jobPostRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));

        jobPost.setCompanyName(request.getCompanyName());
        jobPost.setJobTitle(request.getJobTitle());
        jobPost.setJobUrl(request.getJobUrl());
        jobPost.setLocation(request.getLocation());
        jobPost.setEmploymentType(request.getEmploymentType());
        jobPost.setSource(request.getSource());
        jobPost.setDescription(request.getDescription());

        JobPost saved = jobPostRepository.save(jobPost);
        publishEmbedEvent(saved);
        return toResponse(saved);
    }

    public void deleteMyJobPost(String email, Long id) {
        User user = getUserByEmail(email);

        JobPost jobPost = jobPostRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));

        try {
            jobPostEmbeddingService.deleteJobPostEmbeddings(id);
        } catch (Exception e) {
            log.warn("Could not delete embeddings for jobPostId={}: {}", id, e.getMessage());
        }

        jobPostRepository.delete(jobPost);
    }

    public List<JobPostResponse> searchMyJobPostsByCompany(String email, String companyName) {
        User user = getUserByEmail(email);
        return jobPostRepository
                .findByUserIdAndCompanyNameContainingIgnoreCaseOrderByCreatedAtDesc(
                        user.getId(), companyName)
                .stream().map(this::toResponse).toList();
    }

    public List<JobPostResponse> searchMyJobPostsByTitle(String email, String jobTitle) {
        User user = getUserByEmail(email);
        return jobPostRepository
                .findByUserIdAndJobTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                        user.getId(), jobTitle)
                .stream().map(this::toResponse).toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void publishEmbedEvent(JobPost jobPost) {
        try {
            embeddingEventProducer.publishJobPostEmbedEvent(
                    jobPost.getId(),
                    jobPost.getUser().getId(),
                    jobPost.getDescription()
            );
        } catch (Exception e) {
            log.warn("Kafka unavailable, falling back to sync embedding for jobPostId={}", jobPost.getId());
            try {
                jobPostEmbeddingService.embedJobPost(
                        jobPost.getId(),
                        jobPost.getUser().getId(),
                        jobPost.getDescription()
                );
            } catch (Exception embedException) {
                log.error("Sync embedding also failed: {}", embedException.getMessage());
            }
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private JobPostResponse toResponse(JobPost jobPost) {
        return JobPostResponse.builder()
                .id(jobPost.getId())
                .companyName(jobPost.getCompanyName())
                .jobTitle(jobPost.getJobTitle())
                .jobUrl(jobPost.getJobUrl())
                .location(jobPost.getLocation())
                .employmentType(jobPost.getEmploymentType())
                .source(jobPost.getSource())
                .description(jobPost.getDescription())
                .userId(jobPost.getUser().getId())
                .createdAt(jobPost.getCreatedAt())
                .updatedAt(jobPost.getUpdatedAt())
                .build();
    }
}
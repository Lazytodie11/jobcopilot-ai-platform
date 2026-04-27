package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.CreateResumeRequest;
import com.yipeng.jobcopilot.dto.ResumeResponse;
import com.yipeng.jobcopilot.dto.UpdateResumeRequest;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.exception.ResumeEmailAlreadyExistsException;
import com.yipeng.jobcopilot.exception.ResumeNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.ResumeRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    public List<ResumeResponse> getAllResumes() {
        return resumeRepository.findAll()
                .stream()
                .map(this::toResumeResponse)
                .toList();
    }

    public Page<ResumeResponse> getResumesWithPagination(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return resumeRepository.findAll(pageable)
                .map(this::toResumeResponse);
    }

    public ResumeResponse getResumeById(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found with id: " + id));

        return toResumeResponse(resume);
    }

    public ResumeResponse getResumeByEmail(String email) {
        Resume resume = resumeRepository.findByEmail(email)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found with email: " + email));

        return toResumeResponse(resume);
    }

    public List<ResumeResponse> searchResumesByTitle(String keyword) {
        return resumeRepository.findByTitleContainingIgnoreCase(keyword)
                .stream()
                .map(this::toResumeResponse)
                .toList();
    }

    public ResumeResponse createResumeForUser(Long userId, CreateResumeRequest request) {
        User user = getUserEntityById(userId);
        checkResumeEmailAvailableForCreate(request.getEmail());

        Resume resume = Resume.builder()
                .title(request.getTitle())
                .candidateName(request.getCandidateName())
                .email(request.getEmail())
                .content(request.getContent())
                .user(user)
                .build();

        return toResumeResponse(resumeRepository.save(resume));
    }

    public List<ResumeResponse> getResumesByUserId(Long userId) {
        getUserEntityById(userId);

        return resumeRepository.findByUserId(userId)
                .stream()
                .map(this::toResumeResponse)
                .toList();
    }

    public ResumeResponse getResumeByUserIdAndResumeId(Long userId, Long resumeId) {
        getUserEntityById(userId);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + resumeId + " for user id: " + userId
                ));

        return toResumeResponse(resume);
    }

    public ResumeResponse updateResumeByUserIdAndResumeId(Long userId, Long resumeId, UpdateResumeRequest request) {
        getUserEntityById(userId);

        Resume existingResume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + resumeId + " for user id: " + userId
                ));

        checkResumeEmailAvailableForUpdate(request.getEmail(), resumeId);

        existingResume.setTitle(request.getTitle());
        existingResume.setCandidateName(request.getCandidateName());
        existingResume.setEmail(request.getEmail());
        existingResume.setContent(request.getContent());

        return toResumeResponse(resumeRepository.save(existingResume));
    }

    public void deleteResumeByUserIdAndResumeId(Long userId, Long resumeId) {
        getUserEntityById(userId);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + resumeId + " for user id: " + userId
                ));

        resumeRepository.delete(resume);
    }

    public List<ResumeResponse> getMyResumes(String email) {
        User user = getUserEntityByEmail(email);

        return resumeRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResumeResponse)
                .toList();
    }

    public ResumeResponse createMyResume(String email, CreateResumeRequest request) {
        User user = getUserEntityByEmail(email);
        checkResumeEmailAvailableForCreate(request.getEmail());

        Resume resume = Resume.builder()
                .title(request.getTitle())
                .candidateName(request.getCandidateName())
                .email(request.getEmail())
                .content(request.getContent())
                .user(user)
                .build();

        return toResumeResponse(resumeRepository.save(resume));
    }

    public ResumeResponse getMyResumeById(String email, Long resumeId) {
        User user = getUserEntityByEmail(email);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + resumeId + " for current user"
                ));

        return toResumeResponse(resume);
    }

    public ResumeResponse updateMyResume(String email, Long resumeId, UpdateResumeRequest request) {
        User user = getUserEntityByEmail(email);

        Resume existingResume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + resumeId + " for current user"
                ));

        checkResumeEmailAvailableForUpdate(request.getEmail(), resumeId);

        existingResume.setTitle(request.getTitle());
        existingResume.setCandidateName(request.getCandidateName());
        existingResume.setEmail(request.getEmail());
        existingResume.setContent(request.getContent());

        return toResumeResponse(resumeRepository.save(existingResume));
    }

    public void deleteMyResume(String email, Long resumeId) {
        User user = getUserEntityByEmail(email);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + resumeId + " for current user"
                ));

        resumeRepository.delete(resume);
    }

    public ResumeResponse updateResume(Long id, UpdateResumeRequest request) {
        Resume existingResume = resumeRepository.findById(id)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found with id: " + id));

        checkResumeEmailAvailableForUpdate(request.getEmail(), id);

        existingResume.setTitle(request.getTitle());
        existingResume.setCandidateName(request.getCandidateName());
        existingResume.setEmail(request.getEmail());
        existingResume.setContent(request.getContent());

        return toResumeResponse(resumeRepository.save(existingResume));
    }

    public void deleteResume(Long id) {
        Resume existingResume = resumeRepository.findById(id)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found with id: " + id));

        resumeRepository.delete(existingResume);
    }

    private User getUserEntityById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }

    private User getUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    private void checkResumeEmailAvailableForCreate(String email) {
        resumeRepository.findByEmail(email).ifPresent(resume -> {
            throw new ResumeEmailAlreadyExistsException("Resume email already exists: " + email);
        });
    }

    private void checkResumeEmailAvailableForUpdate(String email, Long currentResumeId) {
        resumeRepository.findByEmail(email).ifPresent(resume -> {
            if (!resume.getId().equals(currentResumeId)) {
                throw new ResumeEmailAlreadyExistsException("Resume email already exists: " + email);
            }
        });
    }

    private ResumeResponse toResumeResponse(Resume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .title(resume.getTitle())
                .candidateName(resume.getCandidateName())
                .email(resume.getEmail())
                .content(resume.getContent())
                .userId(resume.getUser().getId())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }
}
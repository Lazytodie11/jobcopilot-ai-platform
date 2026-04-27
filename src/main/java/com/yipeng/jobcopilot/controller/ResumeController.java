package com.yipeng.jobcopilot.controller;

import com.yipeng.jobcopilot.dto.ApiResponse;
import com.yipeng.jobcopilot.dto.CreateResumeRequest;
import com.yipeng.jobcopilot.dto.PagedResponse;
import com.yipeng.jobcopilot.dto.ResumeResponse;
import com.yipeng.jobcopilot.dto.UpdateResumeRequest;
import com.yipeng.jobcopilot.service.EmbeddingEventProducer;
import com.yipeng.jobcopilot.service.PdfParserService;
import com.yipeng.jobcopilot.service.ResumeEmbeddingService;
import com.yipeng.jobcopilot.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final PdfParserService pdfParserService;
    private final ResumeEmbeddingService resumeEmbeddingService;
    private final EmbeddingEventProducer embeddingEventProducer;

    // ── PDF upload ─────────────────────────────────────────────────────────────

    /**
     * Upload a PDF resume.
     * Extracts text, saves to DB, then publishes a Kafka event for async embedding.
     * Returns immediately — embedding happens in the background.
     */
    @PostMapping("/me/upload")
    public ApiResponse<ResumeResponse> uploadPdfResume(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("candidateName") String candidateName,
            @RequestParam("resumeEmail") String resumeEmail
    ) {
        String userEmail = authentication.getName();

        // Step 1: extract text from PDF (quality check included)
        String content = pdfParserService.extractText(file);

        // Step 2: save resume to database
        CreateResumeRequest request = new CreateResumeRequest();
        request.setTitle(title);
        request.setCandidateName(candidateName);
        request.setEmail(resumeEmail);
        request.setContent(content);

        ResumeResponse saved = resumeService.createMyResume(userEmail, request);

        // Step 3: publish async embedding event to Kafka
        // Returns immediately — consumer handles embedding in background
        try {
            embeddingEventProducer.publishResumeEmbedEvent(
                    saved.getId(), saved.getUserId(), content);
        } catch (Exception e) {
            log.error("Failed to publish ResumeEmbedEvent for resumeId={}: {}",
                    saved.getId(), e.getMessage());
            // Non-fatal: resume is saved, embedding will need manual trigger
        }

        return ApiResponse.success("Resume uploaded successfully. Embedding in progress.", saved);
    }

    // ── Re-embed existing resume ───────────────────────────────────────────────

    /**
     * Trigger embedding for an existing resume.
     * Also, async — publishes to Kafka instead of blocking.
     */
    @PostMapping("/me/{resumeId}/embed")
    public ApiResponse<Object> embedExistingResume(
            Authentication authentication,
            @PathVariable Long resumeId
    ) {
        String userEmail = authentication.getName();

        ResumeResponse resume = resumeService.getMyResumeById(userEmail, resumeId);

        if (resume.getContent() == null || resume.getContent().isBlank()) {
            return ApiResponse.failure("Resume content is empty — nothing to embed");
        }

        embeddingEventProducer.publishResumeEmbedEvent(
                resume.getId(), resume.getUserId(), resume.getContent());

        log.info("Published re-embed event for resumeId={} user={}", resumeId, userEmail);
        return ApiResponse.success("Embedding triggered. Processing in background.", null);
    }

    // ── Authenticated current-user resume APIs ─────────────────────────────────

    @GetMapping("/me")
    public ApiResponse<List<ResumeResponse>> getMyResumes(Authentication authentication) {
        return ApiResponse.success("My resumes fetched successfully",
                resumeService.getMyResumes(authentication.getName()));
    }

    @PostMapping("/me")
    public ApiResponse<ResumeResponse> createMyResume(
            Authentication authentication,
            @Valid @RequestBody CreateResumeRequest request
    ) {
        ResumeResponse created = resumeService.createMyResume(authentication.getName(), request);
        return ApiResponse.success("Resume created successfully", created);
    }

    @GetMapping("/me/{resumeId}")
    public ApiResponse<ResumeResponse> getMyResumeById(
            Authentication authentication,
            @PathVariable Long resumeId
    ) {
        return ApiResponse.success("Resume fetched successfully",
                resumeService.getMyResumeById(authentication.getName(), resumeId));
    }

    @PutMapping("/me/{resumeId}")
    public ApiResponse<ResumeResponse> updateMyResume(
            Authentication authentication,
            @PathVariable Long resumeId,
            @Valid @RequestBody UpdateResumeRequest request
    ) {
        ResumeResponse updated = resumeService.updateMyResume(
                authentication.getName(), resumeId, request);
        return ApiResponse.success("Resume updated successfully", updated);
    }

    @DeleteMapping("/me/{resumeId}")
    public ApiResponse<Object> deleteMyResume(
            Authentication authentication,
            @PathVariable Long resumeId
    ) {
        resumeService.deleteMyResume(authentication.getName(), resumeId);
        try {
            resumeEmbeddingService.deleteResumeEmbeddings(resumeId);
        } catch (Exception e) {
            log.warn("Could not delete embeddings for resumeId={}: {}", resumeId, e.getMessage());
        }
        return ApiResponse.success("Resume deleted successfully", null);
    }

    // ── Deprecated admin-style APIs ────────────────────────────────────────────

    @GetMapping
    public ApiResponse<List<ResumeResponse>> getAllResumes() {
        return ApiResponse.success("Resumes fetched successfully",
                resumeService.getAllResumes());
    }

    @GetMapping("/paged")
    public ApiResponse<PagedResponse<ResumeResponse>> getResumesWithPagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Page<ResumeResponse> resumesPage = resumeService.getResumesWithPagination(page, size);
        PagedResponse<ResumeResponse> response = new PagedResponse<>(
                resumesPage.getContent(), resumesPage.getNumber(), resumesPage.getSize(),
                resumesPage.getTotalElements(), resumesPage.getTotalPages(),
                resumesPage.hasNext(), resumesPage.hasPrevious());
        return ApiResponse.success("Paged resumes fetched successfully", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeResponse> getResumeById(@PathVariable Long id) {
        return ApiResponse.success("Resume fetched successfully",
                resumeService.getResumeById(id));
    }

    @GetMapping("/search/by-email")
    public ApiResponse<ResumeResponse> getResumeByEmail(@RequestParam String email) {
        return ApiResponse.success("Resume fetched successfully by email",
                resumeService.getResumeByEmail(email));
    }

    @GetMapping("/search/by-title")
    public ApiResponse<List<ResumeResponse>> searchResumesByTitle(@RequestParam String keyword) {
        return ApiResponse.success("Resumes fetched successfully by title keyword",
                resumeService.searchResumesByTitle(keyword));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResumeResponse> updateResume(
            @PathVariable Long id,
            @Valid @RequestBody UpdateResumeRequest request
    ) {
        return ApiResponse.success("Resume updated successfully",
                resumeService.updateResume(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> deleteResume(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return ApiResponse.success("Resume deleted successfully", null);
    }
}
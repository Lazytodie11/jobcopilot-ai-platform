package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.CoverLetterResponse;
import com.yipeng.jobcopilot.entity.JobPost;
import com.yipeng.jobcopilot.entity.MatchResult;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.exception.JobPostNotFoundException;
import com.yipeng.jobcopilot.exception.ResumeNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.JobPostRepository;
import com.yipeng.jobcopilot.repository.MatchResultRepository;
import com.yipeng.jobcopilot.repository.ResumeRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int RAG_TOP_K = 5;

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final MatchResultRepository matchResultRepository;
    private final ResumeEmbeddingService resumeEmbeddingService;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generates a tailored cover letter for a given resume + job post.
     *
     * Strategy:
     *   1. RAG: retrieve most relevant resume chunks for "cover letter work experience skills"
     *   2. Inject resume chunks + full JD + cached match context into prompt
     *   3. Generate a professional, specific cover letter via GPT
     */
    public CoverLetterResponse generate(String email, Long resumeId, Long jobPostId,
                                        String extraContext) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));

        String resumeContext = retrieveResumeContext(resume);
        String matchContext  = buildMatchContext(resumeId, jobPostId);
        String prompt        = buildPrompt(resume, jobPost, resumeContext, matchContext, extraContext);

        String coverLetter = callOpenAi(prompt);

        log.info("Cover letter generated for user={} resume={} jobPost={}",
                email, resumeId, jobPostId);

        return CoverLetterResponse.builder()
                .resumeId(resumeId)
                .jobPostId(jobPostId)
                .companyName(jobPost.getCompanyName())
                .jobTitle(jobPost.getJobTitle())
                .coverLetter(coverLetter)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * RAG: retrieve the most relevant resume chunks.
     * Uses a general query that covers work experience, skills, and projects —
     * which are the most important sections for a cover letter.
     */
    private String retrieveResumeContext(Resume resume) {
        try {
            // General query to pull work experience + skills sections
            List<Document> chunks = resumeEmbeddingService.searchResumeChunks(
                    "work experience skills projects achievements", resume.getId(), RAG_TOP_K);

            if (!chunks.isEmpty()) {
                log.debug("Cover letter RAG: retrieved {} chunks for resumeId={}",
                        chunks.size(), resume.getId());
                return chunks.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n\n---\n\n"));
            }

            // Fallback to full content if no embeddings exist
            log.warn("Cover letter RAG: no chunks for resumeId={}, using full content",
                    resume.getId());
            return resume.getContent() != null ? resume.getContent() : "";

        } catch (Exception e) {
            log.warn("Cover letter RAG failed for resumeId={}: {}", resume.getId(), e.getMessage());
            return resume.getContent() != null ? resume.getContent() : "";
        }
    }

    /**
     * Reuses cached match result if available, to avoid re-running analysis.
     */
    private String buildMatchContext(Long resumeId, Long jobPostId) {
        try {
            Optional<MatchResult> cached = matchResultRepository
                    .findTopByResumeIdAndJobPostIdOrderByCreatedAtDesc(resumeId, jobPostId);

            if (cached.isPresent()) {
                MatchResult result = cached.get();
                return "Match score: %d%%. Summary: %s"
                        .formatted(result.getMatchScore(), safe(result.getAiSummary()));
            }
        } catch (Exception e) {
            log.warn("Could not retrieve match context for cover letter: {}", e.getMessage());
        }
        return "";
    }

    private String buildPrompt(Resume resume, JobPost jobPost,
                               String resumeContext, String matchContext,
                               String extraContext) {
        String extra = (extraContext != null && !extraContext.isBlank())
                ? "\nAdditional instructions from the candidate: " + extraContext
                : "";

        return """
                You are an expert career coach writing a professional cover letter.

                Write a compelling, specific cover letter for the candidate below.
                The letter should:
                - Be 3-4 paragraphs, professional but personable in tone
                - Open with a strong hook that mentions the specific company and role
                - Highlight 2-3 of the most relevant experiences from the resume that match the JD
                - Address any notable skill alignment or gap honestly but positively
                - Close with a confident call to action
                - NOT use generic filler phrases like "I am a hard worker" or "team player"
                - NOT include [placeholder] brackets — write the full letter ready to send

                Candidate name: %s

                Relevant Resume Sections:
                %s

                Job Description (%s at %s):
                %s

                %s
                %s

                Write only the cover letter text, starting with "Dear Hiring Manager," or the
                recruiter's name if known. Do not include any preamble or explanation.
                """.formatted(
                safe(resume.getCandidateName()),
                resumeContext,
                safe(jobPost.getJobTitle()),
                safe(jobPost.getCompanyName()),
                safe(jobPost.getDescription()),
                matchContext.isBlank() ? "" : "Match context: " + matchContext,
                extra);
    }

    private String callOpenAi(String prompt) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model  = System.getenv("OPENAI_MODEL");

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OPENAI_API_KEY is not configured");
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4.1-mini";
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.7  // slightly higher for more natural writing
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                OPENAI_URL, new HttpEntity<>(requestBody, headers), Map.class);

        if (response.getBody() == null) {
            throw new RuntimeException("OpenAI returned empty response");
        }

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.getBody().get("choices");

        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI returned no choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            throw new RuntimeException("OpenAI returned empty message content");
        }

        return message.get("content").toString().trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
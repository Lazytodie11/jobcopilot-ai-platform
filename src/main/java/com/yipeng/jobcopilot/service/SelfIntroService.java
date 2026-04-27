package com.yipeng.jobcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.jobcopilot.dto.SelfIntroResponse;
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
public class SelfIntroService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int RAG_TOP_K = 5;

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final MatchResultRepository matchResultRepository;
    private final ResumeEmbeddingService resumeEmbeddingService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SelfIntroResponse generate(String email, Long resumeId, Long jobPostId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));

        String resumeContext = retrieveResumeContext(resume);
        String matchContext  = buildMatchContext(resumeId, jobPostId);
        String prompt        = buildPrompt(resume, jobPost, resumeContext, matchContext);

        String[] versions = callOpenAiAndParse(prompt);

        log.info("Self-intro generated for user={} resume={} jobPost={}",
                email, resumeId, jobPostId);

        return SelfIntroResponse.builder()
                .resumeId(resumeId)
                .jobPostId(jobPostId)
                .companyName(jobPost.getCompanyName())
                .jobTitle(jobPost.getJobTitle())
                .thirtySeconds(versions[0])
                .oneMinute(versions[1])
                .twoMinutes(versions[2])
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String retrieveResumeContext(Resume resume) {
        try {
            List<Document> chunks = resumeEmbeddingService.searchResumeChunks(
                    "education experience skills background summary", resume.getId(), RAG_TOP_K);

            if (!chunks.isEmpty()) {
                log.debug("Self-intro RAG: retrieved {} chunks for resumeId={}",
                        chunks.size(), resume.getId());
                return chunks.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n\n---\n\n"));
            }

            log.warn("Self-intro RAG: no chunks for resumeId={}, using full content",
                    resume.getId());
            return safe(resume.getContent());

        } catch (Exception e) {
            log.warn("Self-intro RAG failed for resumeId={}: {}", resume.getId(), e.getMessage());
            return safe(resume.getContent());
        }
    }

    private String buildMatchContext(Long resumeId, Long jobPostId) {
        try {
            Optional<MatchResult> cached = matchResultRepository
                    .findTopByResumeIdAndJobPostIdOrderByCreatedAtDesc(resumeId, jobPostId);
            if (cached.isPresent()) {
                MatchResult r = cached.get();
                return "Match score: %d%%. Summary: %s"
                        .formatted(r.getMatchScore(), safe(r.getAiSummary()));
            }
        } catch (Exception e) {
            log.warn("Could not retrieve match context for self-intro: {}", e.getMessage());
        }
        return "";
    }

    private String buildPrompt(Resume resume, JobPost jobPost,
                               String resumeContext, String matchContext) {
        return """
                You are an expert career coach helping a candidate prepare for job interviews.
                Write three versions of a self-introduction for this specific role.

                Requirements for each version:
                - 30-second version (~80 words): name, degree, 1 key experience, why this company
                - 1-minute version (~150 words): name, degree, 2 key experiences with impact, why this role
                - 2-minute version (~300 words): full narrative — education, all relevant experiences
                  with specific details and numbers, connection to this company's mission, enthusiasm to grow

                Rules:
                - Be specific: reference actual companies, technologies, and metrics from the resume
                - Each version must be tailored to THIS job at THIS company — not a generic intro
                - Write in first person, natural spoken English (not overly formal)
                - Do NOT use placeholder brackets
                - Do NOT start all versions the same way

                Return ONLY a JSON object (no markdown, no extra text):
                {
                  "thirtySeconds": "...",
                  "oneMinute": "...",
                  "twoMinutes": "..."
                }

                Candidate: %s
                Resume sections:
                %s

                Job: %s at %s
                Job Description:
                %s

                %s
                """.formatted(
                safe(resume.getCandidateName()),
                resumeContext,
                safe(jobPost.getJobTitle()),
                safe(jobPost.getCompanyName()),
                safe(jobPost.getDescription()),
                matchContext.isBlank() ? "" : "Match context: " + matchContext);
    }

    private String[] callOpenAiAndParse(String prompt) {
        String raw = callOpenAi(prompt);

        try {
            String json = raw
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);

            return new String[]{
                    root.path("thirtySeconds").asText(""),
                    root.path("oneMinute").asText(""),
                    root.path("twoMinutes").asText("")
            };

        } catch (Exception e) {
            log.error("Failed to parse self-intro JSON response: {}", e.getMessage());
            // Fallback: put the raw text in all three fields
            return new String[]{raw, raw, raw};
        }
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
                "temperature", 0.6
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
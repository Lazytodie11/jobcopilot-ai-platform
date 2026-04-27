package com.yipeng.jobcopilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.jobcopilot.dto.MockInterviewQuestion;
import com.yipeng.jobcopilot.dto.MockInterviewResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int RAG_TOP_K = 5;

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final MatchResultRepository matchResultRepository;
    private final ResumeEmbeddingService resumeEmbeddingService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MockInterviewResponse generate(String email, Long resumeId, Long jobPostId,
                                          String focusArea) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));

        String resumeContext = retrieveResumeContext(resume);
        String matchContext  = buildMatchContext(resumeId, jobPostId);
        String prompt        = buildPrompt(resume, jobPost, resumeContext, matchContext, focusArea);

        List<MockInterviewQuestion> questions = callOpenAiAndParse(prompt);

        log.info("Mock interview questions generated for user={} resume={} jobPost={}",
                email, resumeId, jobPostId);

        return MockInterviewResponse.builder()
                .resumeId(resumeId)
                .jobPostId(jobPostId)
                .companyName(jobPost.getCompanyName())
                .jobTitle(jobPost.getJobTitle())
                .questions(questions)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String retrieveResumeContext(Resume resume) {
        try {
            List<Document> chunks = resumeEmbeddingService.searchResumeChunks(
                    "work experience projects skills technical achievements", resume.getId(), RAG_TOP_K);

            if (!chunks.isEmpty()) {
                log.debug("Mock interview RAG: retrieved {} chunks for resumeId={}",
                        chunks.size(), resume.getId());
                return chunks.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n\n---\n\n"));
            }

            log.warn("Mock interview RAG: no chunks for resumeId={}, using full content",
                    resume.getId());
            return safe(resume.getContent());

        } catch (Exception e) {
            log.warn("Mock interview RAG failed for resumeId={}: {}", resume.getId(), e.getMessage());
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
            log.warn("Could not retrieve match context for mock interview: {}", e.getMessage());
        }
        return "";
    }

    private String buildPrompt(Resume resume, JobPost jobPost,
                               String resumeContext, String matchContext,
                               String focusArea) {
        String focusLine = (focusArea != null && !focusArea.isBlank())
                ? "Focus especially on: " + focusArea + "."
                : "Cover a balanced mix of behavioral, technical, and role-specific questions.";

        return """
                You are an expert technical interviewer preparing a candidate for a specific job interview.
                Generate exactly 6 interview questions tailored to this candidate's background and this job.

                %s

                Rules:
                - Each question must be specific to THIS candidate's resume and THIS job — not generic
                - "whyAsked" must reference something concrete from the resume or JD
                - "answerHint" must give actionable advice, referencing specific resume experiences when possible
                - Categories should include a mix of: Behavioral, Technical, System Design, Role-Specific
                - If the resume has a skill gap vs the JD, include a question addressing it

                Return ONLY a JSON array in this exact format (no markdown, no extra text):
                [
                  {
                    "category": "Behavioral",
                    "question": "...",
                    "whyAsked": "...",
                    "answerHint": "..."
                  }
                ]

                Candidate: %s
                Resume sections:
                %s

                Job: %s at %s
                Job Description:
                %s

                %s
                """.formatted(
                focusLine,
                safe(resume.getCandidateName()),
                resumeContext,
                safe(jobPost.getJobTitle()),
                safe(jobPost.getCompanyName()),
                safe(jobPost.getDescription()),
                matchContext.isBlank() ? "" : "Match context: " + matchContext);
    }

    private List<MockInterviewQuestion> callOpenAiAndParse(String prompt) {
        String raw = callOpenAi(prompt);

        try {
            // Strip markdown fences if model wraps output anyway
            String json = raw
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);
            List<MockInterviewQuestion> questions = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode node : root) {
                    questions.add(MockInterviewQuestion.builder()
                            .category(node.path("category").asText(""))
                            .question(node.path("question").asText(""))
                            .whyAsked(node.path("whyAsked").asText(""))
                            .answerHint(node.path("answerHint").asText(""))
                            .build());
                }
            }

            return questions;

        } catch (Exception e) {
            log.error("Failed to parse mock interview JSON response: {}", e.getMessage());
            // Fallback: return raw content as a single question so the call isn't wasted
            return List.of(MockInterviewQuestion.builder()
                    .category("General")
                    .question(raw)
                    .whyAsked("Could not parse structured response")
                    .answerHint("")
                    .build());
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
                "temperature", 0.4
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
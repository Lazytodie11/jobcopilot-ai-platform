package com.yipeng.jobcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.jobcopilot.dto.BulletSuggestion;
import com.yipeng.jobcopilot.dto.MatchAnalysisResponse;
import com.yipeng.jobcopilot.dto.ResumeSuggestionResponse;
import com.yipeng.jobcopilot.entity.JobPost;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.exception.JobPostNotFoundException;
import com.yipeng.jobcopilot.exception.ResumeNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.JobPostRepository;
import com.yipeng.jobcopilot.repository.ResumeRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeSuggestionService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final MatchAnalysisService matchAnalysisService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResumeSuggestionResponse getSuggestions(String email, Long resumeId, Long jobPostId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found or not owned by user"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found or not owned by user"));

        // Run match analysis first to get matched/missing skill context
        MatchAnalysisResponse matchResult = matchAnalysisService.analyzeMatch(email, resumeId, jobPostId);

        String prompt = buildPrompt(resume, jobPost, matchResult);
        String rawResponse = callOpenAi(prompt);
        return parseResponse(rawResponse, resumeId, jobPostId);
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(Resume resume, JobPost jobPost, MatchAnalysisResponse match) {
        return """
                You are an expert technical career coach helping a candidate improve their resume for a specific job.

                Your task: give concrete, actionable suggestions to make this resume a stronger fit for the job description.

                Resume Content:
                %s

                Job Description:
                %s

                Current Match Analysis:
                - Matched skills: %s
                - Missing required skills: %s
                - Match score: %d%%

                Return JSON only, in this exact format:
                {
                  "overallStrategy": "2-3 sentences on the main positioning shift needed",
                  "bulletSuggestions": [
                    {
                      "original": "exact bullet or weak area from the resume",
                      "improved": "rewritten version tailored to this JD",
                      "reason": "one sentence explaining why this change helps"
                    }
                  ],
                  "skillsToHighlight": ["skill1", "skill2"],
                  "skillsToAcquire": ["skill1", "skill2"],
                  "additionalTips": ["tip1", "tip2"]
                }

                Rules:
                - bulletSuggestions: provide 3 to 5 specific rewrites. If the resume lacks concrete bullets, suggest new ones based on likely experience.
                - skillsToHighlight: skills already in the resume that are relevant but undersold.
                - skillsToAcquire: max 3 skills. Prioritize the ones with the highest ROI for this role.
                - additionalTips: max 4 tips. Be specific, not generic ("quantify impact" is vague — "add request throughput or latency numbers to your infra work" is good).
                - Do NOT include markdown. Return raw JSON only.
                """.formatted(
                safe(resume.getContent()),
                safe(jobPost.getDescription()),
                match.getMatchedSkills(),
                match.getMissingSkills(),
                match.getWeightedMatchScore()
        );
    }

    // ── OpenAI call ───────────────────────────────────────────────────────────

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
                "temperature", 0.4   // slightly higher than extraction — we want creative rewrites
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
            throw new RuntimeException("OpenAI returned empty message");
        }

        return message.get("content").toString().trim();
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private ResumeSuggestionResponse parseResponse(String raw, Long resumeId, Long jobPostId) {
        try {
            String json = raw
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);

            String overallStrategy = root.path("overallStrategy").asText("");

            List<BulletSuggestion> bulletSuggestions = new ArrayList<>();
            for (JsonNode node : root.path("bulletSuggestions")) {
                bulletSuggestions.add(new BulletSuggestion(
                        node.path("original").asText(""),
                        node.path("improved").asText(""),
                        node.path("reason").asText("")
                ));
            }

            List<String> skillsToHighlight = parseStringList(root.path("skillsToHighlight"));
            List<String> skillsToAcquire   = parseStringList(root.path("skillsToAcquire"));
            List<String> additionalTips     = parseStringList(root.path("additionalTips"));

            return ResumeSuggestionResponse.builder()
                    .resumeId(resumeId)
                    .jobPostId(jobPostId)
                    .overallStrategy(overallStrategy)
                    .bulletSuggestions(bulletSuggestions)
                    .skillsToHighlight(skillsToHighlight)
                    .skillsToAcquire(skillsToAcquire)
                    .additionalTips(additionalTips)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse suggestion response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse AI suggestion response: " + e.getMessage(), e);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String val = item.asText("").trim();
                if (!val.isBlank()) result.add(val);
            }
        }
        return result;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
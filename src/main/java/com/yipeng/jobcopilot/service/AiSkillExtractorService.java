package com.yipeng.jobcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.jobcopilot.dto.AiSkillExtractionResult;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Uses OpenAI to extract structured technical skills from a job description.
 *
 * Solves two problems that keyword matching cannot:
 *   1. "or" vs "and" — "Go, Python, or Java" → preferredSkills, not requiredSkills
 *   2. Unknown skills — returns skills not in our local registry (e.g. "Temporal", "Argo CD")
 *
 * This service only does extraction. Matching logic stays in MatchAnalysisService.
 */
@Service
public class AiSkillExtractorService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extracts required and preferred skills from a job description using GPT.
     *
     * @param jdText the full job description text
     * @return AiSkillExtractionResult with requiredSkills and preferredSkills
     * @throws RuntimeException if the API call fails or returns unparseable output
     */
    public AiSkillExtractionResult extractSkillsFromJd(String jdText) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model  = System.getenv("OPENAI_MODEL");

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OPENAI_API_KEY is not configured");
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4.1-mini";
        }

        String prompt = buildPrompt(jdText);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.1   // low temperature = more consistent structured output
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, request, Map.class);

        if (response.getBody() == null) {
            throw new RuntimeException("OpenAI returned empty response");
        }

        String rawContent = extractContent(response.getBody());
        return parseResult(rawContent);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String buildPrompt(String jdText) {
        return """
                You are a technical recruiter parsing a job description.
                Extract only concrete technical skills: programming languages, frameworks, databases, tools, and platforms.

                Classify each skill as:
                - "requiredSkills": explicitly required, or the primary stack the role is built on
                - "preferredSkills": marked as "nice to have", "preferred", "a plus", OR listed as alternatives with "or" / "e.g."

                Return JSON only, in this exact format:
                {
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": ["Docker", "Kubernetes"]
                }

                Rules:
                - Use canonical names: "JavaScript" not "JS", "PostgreSQL" not "Postgres", "Kubernetes" not "K8s"
                - Do NOT include soft skills, degrees, locations, or vague concepts like "cloud" or "distributed systems"
                - Do NOT include markdown or any text outside the JSON object
                - If a section says "e.g., Go, Python, Java, or C++" — all of these go to preferredSkills (they are examples/alternatives)
                - If a skill has no strong signal either way, put it in preferredSkills

                Job Description:
                %s
                """.formatted(jdText == null ? "" : jdText);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> responseBody) {
        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) responseBody.get("choices");

        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI returned no choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

        if (message == null || message.get("content") == null) {
            throw new RuntimeException("OpenAI returned empty message content");
        }

        return message.get("content").toString().trim();
    }

    private AiSkillExtractionResult parseResult(String rawContent) {
        try {
            // Strip markdown fences if the model wraps the JSON anyway
            String json = rawContent
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);

            List<String> required  = parseStringList(root.path("requiredSkills"));
            List<String> preferred = parseStringList(root.path("preferredSkills"));

            return new AiSkillExtractionResult(required, preferred);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI skill extraction response: " + e.getMessage(), e);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String val = item.asText("").trim();
                if (!val.isBlank()) {
                    result.add(val);
                }
            }
        }
        return result;
    }
}
package com.yipeng.jobcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> analyzeMatch(String resumeContent, String jobDescription) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OPENAI_API_KEY is not configured");
        }

        if (model == null || model.isBlank()) {
            model = "gpt-4.1-mini";
        }

        String url = "https://api.openai.com/v1/chat/completions";

        String prompt = """
                You are an expert technical recruiter and backend engineering interviewer.

                Compare the resume and job description.

                Return JSON only in this exact format:
                {
                  "matchScore": 0,
                  "aiSummary": "short explanation"
                }

                Rules:
                - matchScore must be an integer from 0 to 100.
                - Be strict and realistic.
                - Do not include markdown.
                - Do not include extra text outside JSON.

                Resume:
                %s

                Job Description:
                %s
                """.formatted(safeText(resumeContent), safeText(jobDescription));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getBody() == null) {
            throw new RuntimeException("OpenAI API returned empty response");
        }

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.getBody().get("choices");

        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI API returned no choices");
        }

        Map<String, Object> message =
                (Map<String, Object>) choices.get(0).get("message");

        if (message == null || message.get("content") == null) {
            throw new RuntimeException("OpenAI API returned empty message content");
        }

        String rawContent = message.get("content").toString();

        int matchScore = 0;
        String aiSummary = rawContent;

        try {
            JsonNode jsonNode = objectMapper.readTree(rawContent);
            matchScore = jsonNode.path("matchScore").asInt(0);
            aiSummary = jsonNode.path("aiSummary").asText(rawContent);
        } catch (Exception e) {
            aiSummary = rawContent;
        }

        matchScore = clampScore(matchScore);

        return Map.of(
                "matchScore", matchScore,
                "aiSummary", aiSummary,
                "rawAiResponse", rawContent
        );
    }

    private int clampScore(int score) {
        if (score < 0) {
            return 0;
        }
        if (score > 100) {
            return 100;
        }
        return score;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
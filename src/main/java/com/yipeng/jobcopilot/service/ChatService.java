package com.yipeng.jobcopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.jobcopilot.dto.*;
import com.yipeng.jobcopilot.entity.*;
import com.yipeng.jobcopilot.enumeration.ChatRole;
import com.yipeng.jobcopilot.exception.*;
import com.yipeng.jobcopilot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    // Maximum number of past message pairs (user + assistant) to include in context.
    private static final int MAX_HISTORY_PAIRS = 10;

    // Number of resume chunks to retrieve from pgvector per question.
    private static final int RAG_TOP_K = 4;

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchAnalysisService matchAnalysisService;
    private final ResumeEmbeddingService resumeEmbeddingService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Create session ─────────────────────────────────────────────────────────

    @Transactional
    public ChatSessionResponse createSession(String email, Long resumeId, Long jobPostId) {
        User user = getUser(email);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found"));

        ChatSession session = ChatSession.builder()
                .user(user)
                .resume(resume)
                .jobPost(jobPost)
                .build();

        chatSessionRepository.save(session);

        return toSessionResponse(session, null);
    }

    // ── List sessions ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getMySessions(String email) {
        User user = getUser(email);
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .map(s -> toSessionResponse(s, null))
                .toList();
    }

    // ── Get session with history ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(String email, Long sessionId) {
        User user = getUser(email);
        ChatSession session = getSession(sessionId, user.getId());
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionResponse(session, messages);
    }

    // ── Send message ───────────────────────────────────────────────────────────

    @Transactional
    public ChatMessageResponse sendMessage(String email, Long sessionId, String userContent) {
        User user = getUser(email);
        ChatSession session = getSession(sessionId, user.getId());

        // Save the user's message first
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(ChatRole.USER)
                .content(userContent)
                .build();
        chatMessageRepository.save(userMessage);

        // Load full history for context window
        List<ChatMessage> history = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Build OpenAI messages — pass userContent for RAG retrieval
        List<Map<String, String>> openAiMessages = buildOpenAiMessages(session, history, userContent);

        // Call OpenAI
        String aiContent = callOpenAi(openAiMessages);

        // Save the AI response
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(aiContent)
                .build();
        chatMessageRepository.save(assistantMessage);

        // Touch session updatedAt so it sorts to top in list
        chatSessionRepository.save(session);

        return toChatMessageResponse(assistantMessage);
    }

    // ── Build OpenAI messages ──────────────────────────────────────────────────

    private List<Map<String, String>> buildOpenAiMessages(ChatSession session,
                                                          List<ChatMessage> allHistory,
                                                          String userQuestion) {
        List<Map<String, String>> messages = new ArrayList<>();

        String systemPrompt = buildSystemPrompt(session, userQuestion);
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // Trim history to avoid token overflow
        int maxMessages = MAX_HISTORY_PAIRS * 2;
        List<ChatMessage> trimmed = allHistory.size() > maxMessages
                ? allHistory.subList(allHistory.size() - maxMessages, allHistory.size())
                : allHistory;

        for (ChatMessage msg : trimmed) {
            messages.add(Map.of(
                    "role", msg.getRole() == ChatRole.USER ? "user" : "assistant",
                    "content", msg.getContent()
            ));
        }

        return messages;
    }

    private String buildSystemPrompt(ChatSession session, String userQuestion) {
        Resume resume   = session.getResume();
        JobPost jobPost = session.getJobPost();

        String resumeContext = retrieveResumeContext(userQuestion, resume);
        String matchContext  = buildMatchContext(session);

        return """
                You are an expert career coach helping a candidate improve their job application.

                IMPORTANT: You already have the candidate's resume content below. \
                Do NOT ask the user to provide their resume or bullet points again. \
                Work directly with what is provided. If the content seems limited, \
                do your best with what is available and be transparent about it.

                Relevant Resume Sections:
                %s

                Job Description:
                %s

                %s

                You can help with:
                - Explaining why the match score is what it is
                - Suggesting which experience to highlight or rewrite
                - Rewriting specific bullet points to better fit this JD
                - Answering questions about skill gaps and how to address them
                - Drafting talking points for interviews
                """.formatted(resumeContext, safe(jobPost.getDescription()), matchContext);
    }

    // ── RAG retrieval ──────────────────────────────────────────────────────────

    /**
     * Retrieves the most relevant resume chunks from pgvector for the given question.
     * Falls back to full resume text if pgvector has no data for this resume.
     */
    private String retrieveResumeContext(String userQuestion, Resume resume) {
        try {
            List<Document> chunks = resumeEmbeddingService.searchResumeChunks(
                    userQuestion, resume.getId(), RAG_TOP_K);

            if (!chunks.isEmpty()) {
                log.debug("RAG: retrieved {} chunks for resumeId={}", chunks.size(), resume.getId());
                return chunks.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n\n---\n\n"));
            }

            log.warn("RAG: no chunks found for resumeId={}, falling back to full content",
                    resume.getId());
            return safe(resume.getContent());

        } catch (Exception e) {
            log.warn("RAG: vector search failed for resumeId={}, falling back: {}",
                    resume.getId(), e.getMessage());
            return safe(resume.getContent());
        }
    }

    // ── Match context (cached) ─────────────────────────────────────────────────

    /**
     * Builds match analysis context for the system prompt.
     *
     * Strategy:
     *   1. Check match_results table for an existing result for this resume+jobPost pair
     *   2. If found → use cached result directly (no AI call, no new DB write)
     *   3. If not found → run full AI analysis and save to DB
     *
     * This prevents repeated AI calls and duplicate match_results rows
     * every time the user sends a chat message in the same session.
     */
    private String buildMatchContext(ChatSession session) {
        Long resumeId  = session.getResume().getId();
        Long jobPostId = session.getJobPost().getId();

        try {
            // ── Step 1: check cache ────────────────────────────────────────────
            Optional<MatchResult> cached = matchResultRepository
                    .findTopByResumeIdAndJobPostIdOrderByCreatedAtDesc(resumeId, jobPostId);

            if (cached.isPresent()) {
                MatchResult result = cached.get();
                log.debug("Match context: using cached result id={} for resume={} jobPost={}",
                        result.getId(), resumeId, jobPostId);

                return """
                        Match Analysis (cached):
                        - Match score: %d%%
                        - Summary: %s
                        """.formatted(result.getMatchScore(), safe(result.getAiSummary()));
            }

            // ── Step 2: no cache → run full analysis ───────────────────────────
            log.info("Match context: no cached result found, running analysis for resume={} jobPost={}",
                    resumeId, jobPostId);

            MatchAnalysisResponse match = matchAnalysisService.analyzeMatch(
                    session.getUser().getEmail(), resumeId, jobPostId);

            return """
                    Match Analysis:
                    - Weighted match score: %d%%
                    - Matched skills: %s
                    - Missing skills: %s
                    """.formatted(
                    match.getWeightedMatchScore(),
                    match.getMatchedSkills(),
                    match.getMissingSkills());

        } catch (Exception e) {
            log.warn("Could not build match context for resume={} jobPost={}: {}",
                    resumeId, jobPostId, e.getMessage());
            return "";
        }
    }

    // ── OpenAI call ────────────────────────────────────────────────────────────

    private String callOpenAi(List<Map<String, String>> messages) {
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
                "messages", messages,
                "temperature", 0.5
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

    // ── Mappers ────────────────────────────────────────────────────────────────

    private ChatSessionResponse toSessionResponse(ChatSession session,
                                                  List<ChatMessage> messages) {
        return ChatSessionResponse.builder()
                .id(session.getId())
                .resumeId(session.getResume().getId())
                .jobPostId(session.getJobPost().getId())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messages(messages == null ? null :
                        messages.stream().map(this::toChatMessageResponse).toList())
                .build();
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private ChatSession getSession(Long sessionId, Long userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(
                        "Chat session not found or not owned by user"));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
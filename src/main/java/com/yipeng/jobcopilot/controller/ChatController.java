package com.yipeng.jobcopilot.controller;

import com.yipeng.jobcopilot.dto.*;
import com.yipeng.jobcopilot.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // Create a new chat session for a resume + job post combination
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            Authentication authentication,
            @Valid @RequestBody CreateChatSessionRequest request
    ) {
        String email = authentication.getName();
        ChatSessionResponse session = chatService.createSession(
                email, request.getResumeId(), request.getJobPostId());
        return ApiResponse.success("Chat session created successfully", session);
    }

    // List all my sessions (no messages, just metadata)
    @GetMapping("/sessions/me")
    public ApiResponse<List<ChatSessionResponse>> getMySessions(Authentication authentication) {
        List<ChatSessionResponse> sessions = chatService.getMySessions(authentication.getName());
        return ApiResponse.success("Chat sessions fetched successfully", sessions);
    }

    // Get a specific session with full message history
    @GetMapping("/sessions/{id}")
    public ApiResponse<ChatSessionResponse> getSession(
            Authentication authentication,
            @PathVariable Long id
    ) {
        ChatSessionResponse session = chatService.getSession(authentication.getName(), id);
        return ApiResponse.success("Chat session fetched successfully", session);
    }

    // Send a message and get an AI response
    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request
    ) {
        ChatMessageResponse response = chatService.sendMessage(
                authentication.getName(), id, request.getContent());
        return ApiResponse.success("Message sent successfully", response);
    }
}
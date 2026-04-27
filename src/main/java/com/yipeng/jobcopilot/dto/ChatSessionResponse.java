package com.yipeng.jobcopilot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ChatSessionResponse {
    private Long id;
    private Long resumeId;
    private Long jobPostId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // null when listing sessions, populated when fetching a specific session
    private List<ChatMessageResponse> messages;
}
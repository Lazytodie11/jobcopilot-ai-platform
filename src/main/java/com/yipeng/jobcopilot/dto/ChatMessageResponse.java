package com.yipeng.jobcopilot.dto;

import com.yipeng.jobcopilot.enumeration.ChatRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
    private ChatRole role;
    private String content;
    private LocalDateTime createdAt;
}
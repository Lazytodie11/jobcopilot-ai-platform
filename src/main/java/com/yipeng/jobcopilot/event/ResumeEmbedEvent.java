package com.yipeng.jobcopilot.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka event payload for resume embedding.
 * Published when a resume is created or updated.
 * Consumed by EmbeddingEventConsumer to trigger async embedding.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEmbedEvent {

    private Long resumeId;
    private Long userId;
    private String content;
}
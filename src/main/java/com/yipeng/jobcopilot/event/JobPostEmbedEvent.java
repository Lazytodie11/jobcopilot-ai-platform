package com.yipeng.jobcopilot.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka event payload for job post embedding.
 * Published when a job post is created or updated.
 * Consumed by EmbeddingEventConsumer to trigger async embedding.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostEmbedEvent {

    private Long jobPostId;
    private Long userId;
    private String description;
}
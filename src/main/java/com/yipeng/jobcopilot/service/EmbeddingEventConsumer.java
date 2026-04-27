package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.config.KafkaTopicConfig;
import com.yipeng.jobcopilot.event.JobPostEmbedEvent;
import com.yipeng.jobcopilot.event.ResumeEmbedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes embedding events from Kafka and triggers the actual embedding.
 *
 * This runs asynchronously — the HTTP response has already been returned
 * to the user before this consumer processes the message.
 *
 * Retry behavior: if embedding fails, Kafka will retry delivery
 * based on the consumer group offset (auto-offset-reset=earliest).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingEventConsumer {

    private final ResumeEmbeddingService resumeEmbeddingService;
    private final JobPostEmbeddingService jobPostEmbeddingService;

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_RESUME_EMBED,
            groupId = "jobcopilot-embedding-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeResumeEmbedEvent(ResumeEmbedEvent event) {
        log.info("Consuming ResumeEmbedEvent: resumeId={}", event.getResumeId());
        try {
            resumeEmbeddingService.embedResume(
                    event.getResumeId(),
                    event.getUserId(),
                    event.getContent()
            );
            log.info("Async embedding completed for resumeId={}", event.getResumeId());
        } catch (Exception e) {
            log.error("Failed to embed resumeId={}: {}", event.getResumeId(), e.getMessage());
            // Re-throw so Kafka can handle retry/DLQ if configured
            throw new RuntimeException("Resume embedding failed", e);
        }
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_JOBPOST_EMBED,
            groupId = "jobcopilot-embedding-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeJobPostEmbedEvent(JobPostEmbedEvent event) {
        log.info("Consuming JobPostEmbedEvent: jobPostId={}", event.getJobPostId());
        try {
            jobPostEmbeddingService.embedJobPost(
                    event.getJobPostId(),
                    event.getUserId(),
                    event.getDescription()
            );
            log.info("Async embedding completed for jobPostId={}", event.getJobPostId());
        } catch (Exception e) {
            log.error("Failed to embed jobPostId={}: {}", event.getJobPostId(), e.getMessage());
            throw new RuntimeException("JobPost embedding failed", e);
        }
    }
}
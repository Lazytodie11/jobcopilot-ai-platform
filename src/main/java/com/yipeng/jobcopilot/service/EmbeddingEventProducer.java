package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.config.KafkaTopicConfig;
import com.yipeng.jobcopilot.event.JobPostEmbedEvent;
import com.yipeng.jobcopilot.event.ResumeEmbedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes embedding events to Kafka topics.
 * Called after a resume or job post is saved to the database.
 * The actual embedding happens asynchronously in EmbeddingEventConsumer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishResumeEmbedEvent(Long resumeId, Long userId, String content) {
        ResumeEmbedEvent event = ResumeEmbedEvent.builder()
                .resumeId(resumeId)
                .userId(userId)
                .content(content)
                .build();

        kafkaTemplate.send(KafkaTopicConfig.TOPIC_RESUME_EMBED, resumeId.toString(), event);
        log.info("Published ResumeEmbedEvent for resumeId={}", resumeId);
    }

    public void publishJobPostEmbedEvent(Long jobPostId, Long userId, String description) {
        JobPostEmbedEvent event = JobPostEmbedEvent.builder()
                .jobPostId(jobPostId)
                .userId(userId)
                .description(description)
                .build();

        kafkaTemplate.send(KafkaTopicConfig.TOPIC_JOBPOST_EMBED, jobPostId.toString(), event);
        log.info("Published JobPostEmbedEvent for jobPostId={}", jobPostId);
    }
}
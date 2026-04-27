package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.config.KafkaTopicConfig;
import com.yipeng.jobcopilot.event.JobPostEmbedEvent;
import com.yipeng.jobcopilot.event.ResumeEmbedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmbeddingEventProducer {

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishResumeEmbedEvent(Long resumeId, Long userId, String content) {
        if (kafkaTemplate == null) {
            log.debug("Kafka not available — skipping event for resumeId={}", resumeId);
            throw new RuntimeException("Kafka not available");
        }
        ResumeEmbedEvent event = ResumeEmbedEvent.builder()
                .resumeId(resumeId)
                .userId(userId)
                .content(content)
                .build();
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_RESUME_EMBED, resumeId.toString(), event);
        log.info("Published ResumeEmbedEvent for resumeId={}", resumeId);
    }

    public void publishJobPostEmbedEvent(Long jobPostId, Long userId, String description) {
        if (kafkaTemplate == null) {
            log.debug("Kafka not available — skipping event for jobPostId={}", jobPostId);
            throw new RuntimeException("Kafka not available");
        }
        JobPostEmbedEvent event = JobPostEmbedEvent.builder()
                .jobPostId(jobPostId)
                .userId(userId)
                .description(description)
                .build();
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_JOBPOST_EMBED, jobPostId.toString(), event);
        log.info("Published JobPostEmbedEvent for jobPostId={}", jobPostId);
    }
}
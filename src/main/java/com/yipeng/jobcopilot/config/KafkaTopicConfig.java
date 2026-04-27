package com.yipeng.jobcopilot.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Defines Kafka topics used by JobCopilot.
 * Spring will auto-create these topics on startup if they don't exist.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_RESUME_EMBED   = "resume.embed";
    public static final String TOPIC_JOBPOST_EMBED  = "jobpost.embed";

    @Bean
    public NewTopic resumeEmbedTopic() {
        return TopicBuilder.name(TOPIC_RESUME_EMBED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobPostEmbedTopic() {
        return TopicBuilder.name(TOPIC_JOBPOST_EMBED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
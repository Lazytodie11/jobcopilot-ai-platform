package com.yipeng.jobcopilot.repository;

import com.yipeng.jobcopilot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Returns messages in chronological order — oldest first, so AI sees conversation in order
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
package com.AI.RAG.AgenticRAG.repository;


import com.AI.RAG.AgenticRAG.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    // lấy N tin nhắn gần nhất theo session + user (tách biệt giữa các user)
    List<ChatMessage> findTop20BySessionIdAndCreatedByOrderByCreatedAtDesc(String sessionId, String createdBy);
}
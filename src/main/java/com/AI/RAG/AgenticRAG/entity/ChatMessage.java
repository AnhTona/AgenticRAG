package com.AI.RAG.AgenticRAG.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "chat_message")
@NoArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String sessionId;
    private String role;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    private boolean hasImage;
    private String toolUsed;
    private int iterationCount;

    private Instant createdAt;
    private String createdBy;

    @PrePersist
    public void handleBeforeCreate() {
        if (this.createdBy == null) this.createdBy = "anonymous";
        this.createdAt = Instant.now();
    }
}
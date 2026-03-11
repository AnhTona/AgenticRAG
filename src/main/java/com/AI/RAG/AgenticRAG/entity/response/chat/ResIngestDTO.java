package com.AI.RAG.AgenticRAG.entity.response.chat;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ResIngestDTO {
    private long documentId;
    private String fileName;
    private int totalChunks;
    private String message;
    private Instant createdAt;
}
package com.AI.RAG.AgenticRAG.entity.response.chat;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ResChatDTO {
    private String answer;
    private String sessionId;
    private int totalIterations;
    private List<String> toolsUsed;
    private List<SourceDTO> sources;
    private Instant createdAt;

    @Getter
    @Setter
    public static class SourceDTO {
        private String documentName;
        private String chunkPreview;
        private double score;
    }
}
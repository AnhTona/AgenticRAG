package com.AI.RAG.AgenticRAG.service;

import com.AI.RAG.AgenticRAG.entity.DocumentChunk;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public interface VectorStoreService {

    void storeEmbedding(long chunkId, double[] embedding);

    /** Lưu batch embeddings trực tiếp vào chunks đã có — không query lại DB */
    void storeBatchEmbeddings(List<DocumentChunk> chunks, List<double[]> embeddings);

    List<SearchResult> search(double[] queryEmbedding, int topK, double threshold);

    @Getter
    @Setter
    @AllArgsConstructor
    class SearchResult {
        private long chunkId;
        private String content;
        private String documentName;
        private double score;
    }
}
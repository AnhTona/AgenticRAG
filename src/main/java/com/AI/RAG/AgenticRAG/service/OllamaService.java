package com.AI.RAG.AgenticRAG.service;

import java.util.List;
import java.util.Map;

public interface OllamaService {

    /**
     * Chat thông thường (text only)
     * llama3.2-vision cũng hoạt động tốt cho text chat
     */
    String chat(List<Map<String, Object>> messages, double temperature);

    /**
     * Chat kèm ảnh (multimodal)
     * Dùng llama3.2-vision để phân tích ảnh + trả lời
     */
    String chatWithImage(List<Map<String, Object>> messages, double temperature);

    /**
     * Tạo embedding vector cho 1 text
     * Dùng nomic-embed-text
     */
    double[] getEmbedding(String text);

    /**
     * Tạo embedding vector cho NHIỀU text cùng lúc (batch)
     * GPU xử lý song song → nhanh hơn 10-50x so với gọi từng cái
     */
    List<double[]> getEmbeddingBatch(List<String> texts);
}
package com.AI.RAG.AgenticRAG.service.impl;

import com.AI.RAG.AgenticRAG.service.OllamaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OllamaServiceImpl implements OllamaService {
    @Value("${etco.ollama.base-url}")
    private String baseUrl;

    @Value("${etco.ollama.chat-model}")
    private String model;

    @Value("${etco.ollama.embedding-model}")
    private String embeddingModel;

    @Value("${etco.ollama.keep-alive:30m}")
    private String keepAlive;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public String chat(List<Map<String, Object>> messages, double temperature) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);
            body.put("keep_alive", keepAlive);
            body.put("options", Map.of("temperature", temperature));

            String json = objectMapper.writeValueAsString(body);
            log.info(">>> Ollama Chat: model={}, messages={}", model, messages.size());

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/chat")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> Ollama Chat Failed: code={}, body={}", response.code(), responseBody);
                    return "Xin lỗi, hệ thống AI đang gặp sự cố. Vui lòng thử lại sau.";
                }
                JsonNode node = objectMapper.readTree(responseBody);
                return node.path("message").path("content").asText("");
            }
        } catch (Exception e) {
            log.error(">>> Ollama chat error: {}", e.getMessage());
            return "Xin lỗi, không kết nối được AI. Hãy chắc chắn Ollama đang chạy.";
        }
    }

    @Override
    public String chatWithImage(List<Map<String, Object>> messages, double temperature) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);
            body.put("keep_alive", keepAlive);
            body.put("options", Map.of("temperature", temperature));

            String json = objectMapper.writeValueAsString(body);
            log.info(">>> Ollama Vision: model={}", model);

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/chat")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> Ollama Vision Failed: code={}, body={}", response.code(), responseBody);
                    return "Xin lỗi, không thể xử lý ảnh lúc này.";
                }
                JsonNode node = objectMapper.readTree(responseBody);
                return node.path("message").path("content").asText("");
            }
        } catch (Exception e) {
            log.error(">>> Ollama Vision error: {}", e.getMessage());
            return "Xin lỗi, không thể xử lý ảnh lúc này.";
        }
    }

    @Override
    public double[] getEmbedding(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", embeddingModel);
            body.put("input", text);
            body.put("keep_alive", keepAlive);

            String json = objectMapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/embed")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> OLLAMA EMBED FAILED: code={}, body={}", response.code(), responseBody);
                    return new double[0];
                }
                JsonNode node = objectMapper.readTree(responseBody);

                JsonNode embArr = node.path("embeddings").get(0);
                if (embArr == null || !embArr.isArray()) return new double[0];

                double[] result = new double[embArr.size()];
                for (int i = 0; i < embArr.size(); i++) {
                    result[i] = embArr.get(i).asDouble();
                }
                return result;
            }
        } catch (Exception e) {
            log.error(">>> OLLAMA EMBED ERROR: {}", e.getMessage());
            return new double[0];
        }
    }

    @Override
    public List<double[]> getEmbeddingBatch(List<String> texts) {
        List<double[]> results = new ArrayList<>();
        if (texts == null || texts.isEmpty()) return results;

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", embeddingModel);
            body.put("input", texts);
            body.put("keep_alive", keepAlive);

            String json = objectMapper.writeValueAsString(body);
            log.info(">>> OLLAMA EMBED BATCH: {} texts", texts.size());

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/embed")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> OLLAMA EMBED BATCH FAILED: code={}, body={}", response.code(), responseBody);
                    return results;
                }

                JsonNode node = objectMapper.readTree(responseBody);
                JsonNode embeddings = node.path("embeddings");
                if (!embeddings.isArray()) return results;

                for (int i = 0; i < embeddings.size(); i++) {
                    JsonNode embArr = embeddings.get(i);
                    if (embArr == null || !embArr.isArray()) {
                        results.add(new double[0]);
                        continue;
                    }
                    double[] vec = new double[embArr.size()];
                    for (int j = 0; j < embArr.size(); j++) {
                        vec[j] = embArr.get(j).asDouble();
                    }
                    results.add(vec);
                }

                log.info(">>> OLLAMA EMBED BATCH DONE: {} vectors, dim={}",
                        results.size(), results.isEmpty() ? 0 : results.get(0).length);
                return results;
            }
        } catch (Exception e) {
            log.error(">>> OLLAMA EMBED BATCH ERROR: {}", e.getMessage());
            return results;
        }
    }
}
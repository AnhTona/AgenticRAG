package com.AI.RAG.AgenticRAG.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Gọi REST API sang Main Server (Etco-ticket) để lấy data Event/Ticket/Order
 * Dùng OkHttpClient — giống phong cách OllamaServiceImpl
 */
@Service
@Slf4j
public class MainServerClient {

    @Value("${etco.main-server.base-url}")
    private String mainServerUrl;

    @Value("${etco.internal.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Gọi Main Server tìm sự kiện
     */
    public String fetchEvents(String query) {
        try {
            String url = mainServerUrl + "/api/internal/events/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            log.info(">>> MAIN CLIENT: GET {}", url);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-Internal-Api-Key", apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> MAIN CLIENT events failed: code={}, body={}", response.code(), responseBody);
                    return "Loi khi tim su kien tu he thong chinh.";
                }
                return responseBody;
            }
        } catch (Exception e) {
            log.error(">>> MAIN CLIENT events error: {}", e.getMessage());
            return "Loi ket noi he thong chinh: " + e.getMessage();
        }
    }

    /**
     * Gọi Main Server tìm thông tin vé
     */
    public String fetchTickets(String query) {
        try {
            String url = mainServerUrl + "/api/internal/tickets/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            log.info(">>> MAIN CLIENT: GET {}", url);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-Internal-Api-Key", apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> MAIN CLIENT tickets failed: code={}, body={}", response.code(), responseBody);
                    return "Loi khi tim ve tu he thong chinh.";
                }
                return responseBody;
            }
        } catch (Exception e) {
            log.error(">>> MAIN CLIENT tickets error: {}", e.getMessage());
            return "Loi ket noi he thong chinh: " + e.getMessage();
        }
    }

    /**
     * Gọi Main Server tra cứu đơn hàng
     */
    public String fetchOrder(String query) {
        try {
            String url = mainServerUrl + "/api/internal/orders/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            log.info(">>> MAIN CLIENT: GET {}", url);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-Internal-Api-Key", apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error(">>> MAIN CLIENT order failed: code={}, body={}", response.code(), responseBody);
                    return "Loi khi tra cuu don hang tu he thong chinh.";
                }
                return responseBody;
            }
        } catch (Exception e) {
            log.error(">>> MAIN CLIENT order error: {}", e.getMessage());
            return "Loi ket noi he thong chinh: " + e.getMessage();
        }
    }
}
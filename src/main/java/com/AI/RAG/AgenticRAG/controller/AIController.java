package com.AI.RAG.AgenticRAG.controller;

import com.esco.etco.entity.request.ReqChatDTO;
import com.esco.etco.entity.response.chat.ResChatDTO;
import com.esco.etco.entity.response.chat.ResIngestDTO;
import com.esco.etco.service.AIAgentService;
import com.esco.etco.service.OllamaService;
import com.esco.etco.service.RAGService;
import com.esco.etco.util.SecurityUtil;
import com.esco.etco.util.annotation.ApiMessage;
import com.esco.etco.util.error.IdInvalidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AIController {

    private final AIAgentService aiAgentService;
    private final RAGService ragService;
    private final OllamaService ollamaService;

    public AIController(AIAgentService aiAgentService, RAGService ragService,
                        OllamaService ollamaService) {
        this.aiAgentService = aiAgentService;
        this.ragService = ragService;
        this.ollamaService = ollamaService;
    }

//    // DEBUG: Xem heap memory realtime
//    // GET http://localhost:8080/api/v1/ai/debug-heap
//    @GetMapping("/ai/debug-heap")
//    public ResponseEntity<Map<String, Object>> debugHeap() {
//        Runtime rt = Runtime.getRuntime();
//        Map<String, Object> info = new HashMap<>();
//        info.put("maxHeapMB", rt.maxMemory() / 1024 / 1024);
//        info.put("totalHeapMB", rt.totalMemory() / 1024 / 1024);
//        info.put("freeHeapMB", rt.freeMemory() / 1024 / 1024);
//        info.put("usedHeapMB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
//        return ResponseEntity.ok(info);
//    }

//    // DEBUG: Test embedding KHÔNG file, KHÔNG PDF, KHÔNG DB
//    // GET http://localhost:8080/api/v1/ai/test-embed
//    @GetMapping("/ai/test-embed")
//    public ResponseEntity<Map<String, Object>> testEmbed() {
//        Runtime rt = Runtime.getRuntime();
//        long usedBefore = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("usedHeapBeforeMB", usedBefore);
//        result.put("maxHeapMB", rt.maxMemory() / 1024 / 1024);
//
//        try {
//            long start = System.currentTimeMillis();
//            double[] emb = this.ollamaService.getEmbedding("Xin chao, day la test embedding.");
//            long elapsed = System.currentTimeMillis() - start;
//
//            long usedAfter = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
//
//            result.put("success", emb.length > 0);
//            result.put("embeddingDim", emb.length);
//            result.put("timeMs", elapsed);
//            result.put("usedHeapAfterMB", usedAfter);
//            result.put("heapDeltaMB", usedAfter - usedBefore);
//        } catch (Exception e) {
//            result.put("success", false);
//            result.put("error", e.getMessage());
//        }
//
//        return ResponseEntity.ok(result);
//    }

    @PostMapping("/ai/chat")
    @ApiMessage("Chat với AI (Agentic RAG)")
    public ResponseEntity<ResChatDTO> chat(
            @RequestBody ReqChatDTO dto) throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().orElse("anonymous");
        dto.setUserEmail(email);
        return ResponseEntity.ok(this.aiAgentService.chat(dto));
    }

    @PostMapping("/ai/chat-with-image")
    @ApiMessage("Chat kèm ảnh (Multimodal)")
    public ResponseEntity<ResChatDTO> chatWithImage(
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam("image") MultipartFile image) throws Exception {

        if (image == null || image.isEmpty()) {
            throw new IdInvalidException("Vui lòng gửi ảnh");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IdInvalidException("File phải là ảnh (jpg, png, webp)");
        }

        return ResponseEntity.ok(
                this.aiAgentService.chatWithImage(message, sessionId, image));
    }

    @PostMapping("/ai/ingest")
    @ApiMessage("Upload tài liệu vào Knowledge Base")
    public ResponseEntity<ResIngestDTO> ingest(
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IdInvalidException("Vui lòng upload file");
        }

        // Chỉ chấp nhận PDF và text — KHÔNG chấp nhận ảnh/binary
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        boolean isPdf = "application/pdf".equals(contentType) || fileName.endsWith(".pdf");
        boolean isText = (contentType != null && contentType.startsWith("text/"))
                || fileName.endsWith(".txt") || fileName.endsWith(".csv")
                || fileName.endsWith(".md");

        if (!isPdf && !isText) {
            throw new IdInvalidException(
                    "Chỉ hỗ trợ file PDF hoặc Text (.pdf, .txt, .csv, .md). "
                    + "File ảnh vui lòng dùng API /ai/chat-with-image");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(this.ragService.ingestDocument(file));
    }
}
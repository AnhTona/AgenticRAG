package com.AI.RAG.AgenticRAG.service.impl;

import com.AI.RAG.AgenticRAG.entity.ChatMessage;
import com.AI.RAG.AgenticRAG.entity.request.ReqChatDTO;
import com.AI.RAG.AgenticRAG.entity.response.chat.ResChatDTO;
import com.AI.RAG.AgenticRAG.repository.ChatMessageRepository;
import com.AI.RAG.AgenticRAG.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AIAgentServiceImpl implements AIAgentService {

    @Value("${etco.agent.max-iterations}")
    private int maxIterations;

    @Value("${etco.agent.temperature}")
    private double temperature;

    private final OllamaService ollamaService;
    private final RAGService ragService;
    private final ChatMessageRepository chatMessageRepository;
    private final MainServerClient mainServerClient;  // THAY THẾ 3 Repository cũ

    public AIAgentServiceImpl(OllamaService ollamaService,
                              RAGService ragService,
                              ChatMessageRepository chatMessageRepository,
                              MainServerClient mainServerClient) {
        this.ollamaService = ollamaService;
        this.ragService = ragService;
        this.chatMessageRepository = chatMessageRepository;
        this.mainServerClient = mainServerClient;
    }

    @Override
    public ResChatDTO chat(ReqChatDTO dto) {
        String sessionId = dto.getSessionId() != null
                ? dto.getSessionId()
                : UUID.randomUUID().toString();

        // userEmail truyen tu Main Server — KHONG dung SecurityUtil nua
        String userEmail = dto.getUserEmail() != null ? dto.getUserEmail() : "anonymous";

        log.info(">>> AGENT START: session={}, user={}, message=\"{}\"",
                sessionId, userEmail, dto.getMessage());

        // luu tin nhan user
        saveMessage(sessionId, "user", dto.getMessage(), false, null, 0, userEmail);

        List<String> toolsUsed = new ArrayList<>();
        List<ResChatDTO.SourceDTO> sources = new ArrayList<>();
        StringBuilder allContext = new StringBuilder();

        // RAG Search (knowledge base) — LUON chay
        String ragResult = executeRAGSearch(dto.getMessage(), sources);
        if (!ragResult.isEmpty()) {
            toolsUsed.add("RAG_SEARCH");
            allContext.append("=== TU TAI LIEU (Knowledge Base) ===\n");
            allContext.append(ragResult).append("\n");
        }
        log.info(">>> AGENT: RAG returned {} chars, {} sources",
                ragResult.length(), sources.size());

        // Detect intent va goi tool phu hop
        String intent = detectIntent(dto.getMessage());
        log.info(">>> AGENT: intent={}", intent);

        if (intent.equals("SEARCH_EVENTS") || intent.equals("RAG_SEARCH")) {
            // GOI QUA MAIN SERVER thay vi query DB truc tiep
            String eventResult = this.mainServerClient.fetchEvents(dto.getMessage());
            if (!eventResult.startsWith("Loi") && !eventResult.startsWith("Hien khong")) {
                toolsUsed.add("SEARCH_EVENTS");
                allContext.append("=== SU KIEN TRONG HE THONG ===\n");
                allContext.append(eventResult).append("\n");
            }
        }

        if (intent.equals("SEARCH_TICKETS")) {
            toolsUsed.add("SEARCH_TICKETS");
            String ticketResult = this.mainServerClient.fetchTickets(dto.getMessage());
            allContext.append("=== THONG TIN VE ===\n");
            allContext.append(ticketResult).append("\n");
        }

        if (intent.equals("LOOKUP_ORDER")) {
            toolsUsed.add("LOOKUP_ORDER");
            String orderResult = this.mainServerClient.fetchOrder(dto.getMessage());
            allContext.append("=== DON HANG ===\n");
            allContext.append(orderResult).append("\n");
        }

        log.info(">>> AGENT: total context {} chars, tools={}", allContext.length(), toolsUsed);

        String answer = generateAnswer(
                dto.getMessage(),
                allContext.toString(),
                sources,
                loadHistory(sessionId, userEmail)
        );

        // luu cau tra loi
        saveMessage(sessionId, "assistant", answer, false,
                String.join(",", toolsUsed), 1, userEmail);

        return buildResponse(answer, sessionId, 1, toolsUsed, sources);
    }

    @Override
    public ResChatDTO chatWithImage(String message, String sessionId, String userEmail,
                                    MultipartFile image) throws Exception {
        sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        userEmail = userEmail != null ? userEmail : "anonymous";
        log.info(">>> AGENT VISION: session={}, user={}, message=\"{}\"",
                sessionId, userEmail, message);

        // lưu tin nhắn user
        saveMessage(sessionId, "user", message + " [kèm ảnh]", true, null, 0, userEmail);

        List<String> toolsUsed = new ArrayList<>();
        List<ResChatDTO.SourceDTO> sources = new ArrayList<>();

        // convert ảnh → base64
        String base64 = Base64.getEncoder().encodeToString(image.getBytes());

        // Gửi ảnh + conversation history để nhớ ngữ cảnh
        List<Map<String, Object>> visionMessages = new ArrayList<>();
        visionMessages.add(buildSystemMessage());

        // thêm history để nhớ ngữ cảnh trước đó
        List<Map<String, Object>> history = loadHistory(sessionId, userEmail);
        int historyLimit = Math.min(history.size(), 6);
        for (int i = history.size() - historyLimit; i < history.size(); i++) {
            visionMessages.add(history.get(i));
        }

        // tin nhắn kèm ảnh
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        userMsg.put("images", List.of(base64));
        visionMessages.add(userMsg);

        String imageAnalysis = this.ollamaService.chatWithImage(visionMessages, temperature);
        toolsUsed.add("VISION_ANALYSIS");
        log.info(">>> AGENT VISION: analysis length={}", imageAnalysis.length());

        String combinedQuery = message + " " + imageAnalysis;
        String ragContext = executeRAGSearch(combinedQuery, sources);
        if (!ragContext.isEmpty()) {
            toolsUsed.add("RAG_SEARCH");
        }

        List<Map<String, Object>> finalMessages = new ArrayList<>();
        finalMessages.add(buildSystemMessage());

        StringBuilder prompt = new StringBuilder();
        prompt.append("Người dùng gửi ảnh và hỏi: \"").append(message).append("\"\n\n");
        prompt.append("Kết quả phân tích ảnh:\n").append(imageAnalysis).append("\n\n");
        if (!ragContext.isEmpty()) {
            prompt.append("Thông tin bổ sung từ tài liệu:\n").append(ragContext).append("\n\n");
        }
        prompt.append("Hãy trả lời câu hỏi dựa trên phân tích ảnh và thông tin bổ sung. ");
        prompt.append("Trả lời bằng tiếng Việt, dễ hiểu.");

        finalMessages.add(Map.of("role", "user", "content", prompt.toString()));

        String answer = this.ollamaService.chat(finalMessages, temperature);

        // lưu
        saveMessage(sessionId, "assistant", answer, false,
                String.join(",", toolsUsed), 1, userEmail);

        return buildResponse(answer, sessionId, 1, toolsUsed, sources);
    }

    /**
     * Phát hiện ý định từ câu hỏi
     */
    private String detectIntent(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("sự kiện") || lower.contains("event")
                || lower.contains("show") || lower.contains("concert")
                || lower.contains("lịch") || lower.contains("diễn ra")) {
            return "SEARCH_EVENTS";
        }
        if (lower.contains("vé") || lower.contains("ticket")
                || lower.contains("giá") || lower.contains("price")
                || lower.contains("mua") || lower.contains("còn")) {
            return "SEARCH_TICKETS";
        }
        if (lower.contains("đơn hàng") || lower.contains("order")
                || lower.contains("thanh toán") || lower.contains("ORD-")) {
            return "LOOKUP_ORDER";
        }
        // mặc định: tìm trong knowledge base
        return "RAG_SEARCH";
    }

    /**
     * Tool: Tìm kiếm knowledge base (RAG)
     */
    private String executeRAGSearch(String query, List<ResChatDTO.SourceDTO> sources) {
        log.info(">>> TOOL RAG: searching for \"{}\"",
                query.substring(0, Math.min(80, query.length())));

        List<VectorStoreService.SearchResult> results = this.ragService.retrieveContext(query);
        log.info(">>> TOOL RAG: {} results found", results.size());

        if (results.isEmpty()) return "";

        StringBuilder ctx = new StringBuilder();
        for (VectorStoreService.SearchResult r : results) {
            ctx.append("[").append(r.getDocumentName())
                    .append(" | score=").append(String.format("%.3f", r.getScore()))
                    .append("]\n").append(r.getContent()).append("\n\n");

            ResChatDTO.SourceDTO src = new ResChatDTO.SourceDTO();
            src.setDocumentName(r.getDocumentName());
            src.setChunkPreview(r.getContent().length() > 200
                    ? r.getContent().substring(0, 200) + "..." : r.getContent());
            src.setScore(r.getScore());
            sources.add(src);
        }
        return ctx.toString();
    }

    private String generateAnswer(String userMessage, String toolContext,
                                  List<ResChatDTO.SourceDTO> sources,
                                  List<Map<String, Object>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(buildSystemMessage());

        // conversation history (giữ ngữ cảnh)
        int limit = Math.min(history.size(), 10);
        for (int i = history.size() - limit; i < history.size(); i++) {
            messages.add(history.get(i));
        }

        // user prompt kèm context
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append(userMessage);

        if (toolContext != null && !toolContext.isEmpty()) {
            userPrompt.append("\n\n--- DỮ LIỆU THAM KHẢO ---\n").append(toolContext);
            userPrompt.append("\n--- HẾT DỮ LIỆU ---\n");
            userPrompt.append("\nTrả lời dựa trên dữ liệu tham khảo phía trên. ");
            userPrompt.append("Nếu dữ liệu không đủ, nói rõ. Trả lời bằng tiếng Việt.");
        }

        messages.add(Map.of("role", "user", "content", userPrompt.toString()));

        return this.ollamaService.chat(messages, temperature);
    }

    private Map<String, Object> buildSystemMessage() {
        String systemPrompt = """
                Bạn là trợ lý AI của EvtGo - nền tảng bán vé sự kiện.

                Khả năng:
                1. Trả lời về sự kiện, vé, đơn hàng (từ database hệ thống)
                2. Tìm kiếm thông tin từ tài liệu đã upload (knowledge base)
                3. Phân tích ảnh (poster sự kiện, vé, QR code, hóa đơn)
                4. Nhớ ngữ cảnh hội thoại trước đó

                Quy tắc:
                - Trả lời bằng tiếng Việt, thân thiện, chính xác
                - Giá tiền format: 500,000 VNĐ
                - Nếu không biết → nói rõ "Tôi không có thông tin"
                - Ưu tiên dùng dữ liệu hệ thống khi có
                - Trả lời đúng trọng tâm câu hỏi câu trả lời lang mang như giải thích việc kiếm dữ liệu ở đâu
                """;
        return Map.of("role", "system", "content", systemPrompt);
    }

    /**
     * Load lịch sử hội thoại từ DB
     * userEmail truyền từ Main Server thay vì dùng SecurityUtil
     */
    private List<Map<String, Object>> loadHistory(String sessionId, String userEmail) {
        List<ChatMessage> msgs = this.chatMessageRepository
                .findTop20BySessionIdAndCreatedByOrderByCreatedAtDesc(sessionId, userEmail);
        Collections.reverse(msgs);

        return msgs.stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Lưu tin nhắn — userEmail truyền vào thay vì dùng SecurityUtil
     */
    private void saveMessage(String sessionId, String role, String content,
                             boolean hasImage, String toolUsed, int iteration,
                             String userEmail) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setHasImage(hasImage);
        msg.setToolUsed(toolUsed);
        msg.setIterationCount(iteration);
        msg.setCreatedBy(userEmail);  // set truoc @PrePersist
        this.chatMessageRepository.save(msg);
    }

    private ResChatDTO buildResponse(String answer, String sessionId, int iterations,
                                     List<String> tools, List<ResChatDTO.SourceDTO> sources) {
        ResChatDTO res = new ResChatDTO();
        res.setAnswer(answer);
        res.setSessionId(sessionId);
        res.setTotalIterations(iterations);
        res.setToolsUsed(tools);
        res.setSources(sources);
        res.setCreatedAt(Instant.now());
        return res;
    }
}
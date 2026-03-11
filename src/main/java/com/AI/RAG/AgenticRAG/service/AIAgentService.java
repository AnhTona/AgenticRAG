package com.AI.RAG.AgenticRAG.service;


import com.AI.RAG.AgenticRAG.entity.request.ReqChatDTO;
import com.AI.RAG.AgenticRAG.entity.response.chat.ResChatDTO;
import org.springframework.web.multipart.MultipartFile;

public interface AIAgentService {

    // chat text (Agentic RAG)
    ResChatDTO chat(ReqChatDTO dto);

    // chat kèm ảnh (Multimodal Agentic RAG)
    ResChatDTO chatWithImage(String message, String sessionId, String userEmail, MultipartFile image) throws Exception;
}
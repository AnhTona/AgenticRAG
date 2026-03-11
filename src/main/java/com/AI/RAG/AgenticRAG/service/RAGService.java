package com.AI.RAG.AgenticRAG.service;


import com.AI.RAG.AgenticRAG.entity.response.chat.ResIngestDTO;
import com.AI.RAG.AgenticRAG.entity.response.chat.ResIngestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RAGService {

    // upload file → chunk → embedding → lưu DB
    com.AI.RAG.AgenticRAG.entity.response.chat.ResIngestDTO ingestDocument(MultipartFile file) throws Exception;

    // tìm context phù hợp với câu hỏi
    List<VectorStoreService.SearchResult> retrieveContext(String query);
}
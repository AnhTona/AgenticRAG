package com.AI.RAG.AgenticRAG.repository;


import com.AI.RAG.AgenticRAG.entity.DocumentChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentId(long documentId);

    List<DocumentChunk> findByEmbeddingIsNotNull();

    // Pagination: load từng batch nhỏ thay vì toàn bộ
    Page<DocumentChunk> findByEmbeddingIsNotNull(Pageable pageable);
}
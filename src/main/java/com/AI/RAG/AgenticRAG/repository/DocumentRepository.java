package com.AI.RAG.AgenticRAG.repository;

import com.AI.RAG.AgenticRAG.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
}
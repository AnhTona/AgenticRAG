package com.AI.RAG.AgenticRAG.service.impl;

import com.AI.RAG.AgenticRAG.entity.DocumentChunk;
import com.AI.RAG.AgenticRAG.repository.DocumentChunkRepository;
import com.AI.RAG.AgenticRAG.service.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VectorStoreServiceImpl implements VectorStoreService {

    private final DocumentChunkRepository documentChunkRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int SEARCH_BATCH_SIZE = 50;

    public VectorStoreServiceImpl(DocumentChunkRepository documentChunkRepository,
                                  EntityManager entityManager) {
        this.documentChunkRepository = documentChunkRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void storeEmbedding(long chunkId, double[] embedding) {
        DocumentChunk chunk = this.documentChunkRepository.findById(chunkId).orElse(null);
        if (chunk == null) {
            log.error(">>> VECTOR: chunk id={} not found", chunkId);
            return;
        }
        try {
            chunk.setEmbedding(objectMapper.writeValueAsString(embedding));
            this.documentChunkRepository.save(chunk);
        } catch (Exception e) {
            log.error(">>> VECTOR: error saving embedding chunk id={}", chunkId);
        }
    }

    @Override
    public void storeBatchEmbeddings(List<DocumentChunk> chunks, List<double[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            log.error(">>> VECTOR BATCH: chunks size ({}) != embeddings size ({})",
                    chunks.size(), embeddings.size());
            return;
        }

        try {
            for (int i = 0; i < chunks.size(); i++) {
                double[] emb = embeddings.get(i);
                if (emb.length > 0) {
                    chunks.get(i).setEmbedding(objectMapper.writeValueAsString(emb));
                }
            }
            this.documentChunkRepository.saveAll(chunks);
        } catch (Exception e) {
            log.error(">>> VECTOR BATCH: error saving batch: {}", e.getMessage());
        }
    }

    @Override
    public List<SearchResult> search(double[] queryEmbedding, int topK, double threshold) {
        List<SearchResult> results = new ArrayList<>();
        double maxScoreSeen = 0;
        int totalChunks = 0;

        int page = 0;
        Page<DocumentChunk> chunkPage;

        do {
            chunkPage = this.documentChunkRepository
                    .findByEmbeddingIsNotNull(PageRequest.of(page, SEARCH_BATCH_SIZE));

            for (DocumentChunk chunk : chunkPage.getContent()) {
                totalChunks++;
                try {
                    double[] stored = objectMapper.readValue(chunk.getEmbedding(), double[].class);
                    double score = cosineSimilarity(queryEmbedding, stored);

                    if (score > maxScoreSeen) maxScoreSeen = score;

                    if (score >= threshold) {
                        String docName = chunk.getDocument() != null
                                ? chunk.getDocument().getFileName() : "unknown";
                        results.add(new SearchResult(
                                chunk.getId(), chunk.getContent(), docName, score));
                    }
                } catch (Exception e) {
                    // skip
                }
            }

            entityManager.clear();
            page++;
        } while (chunkPage.hasNext());

        log.info(">>> VECTOR SEARCH: scanned {} chunks, {} matched (threshold={}), maxScore={}",
                totalChunks, results.size(), threshold, String.format("%.4f", maxScoreSeen));

        if (results.isEmpty()) {
            return new ArrayList<>();
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }
}
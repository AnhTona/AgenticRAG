package com.AI.RAG.AgenticRAG.service.impl;


import com.AI.RAG.AgenticRAG.entity.Document;
import com.AI.RAG.AgenticRAG.entity.DocumentChunk;
import com.AI.RAG.AgenticRAG.entity.response.chat.ResIngestDTO;
import com.AI.RAG.AgenticRAG.repository.DocumentChunkRepository;
import com.AI.RAG.AgenticRAG.repository.DocumentRepository;
import com.AI.RAG.AgenticRAG.service.OllamaService;
import com.AI.RAG.AgenticRAG.service.RAGService;
import com.AI.RAG.AgenticRAG.service.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class RAGServiceImpl implements RAGService {

    @Value("${etco.rag.chunk-size}")
    private int chunkSize;

    @Value("${etco.rag.chunk-overlap}")
    private int chunkOverlap;

    @Value("${etco.rag.top-k}")
    private int topK;

    @Value("${etco.rag.similarity-threshold}")
    private double threshold;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final OllamaService ollamaService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RAGServiceImpl(DocumentRepository documentRepository,
                          DocumentChunkRepository documentChunkRepository,
                          OllamaService ollamaService,
                          VectorStoreService vectorStoreService) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ollamaService = ollamaService;
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    @Transactional
    public ResIngestDTO ingestDocument(MultipartFile file) throws Exception {
        logHeap("INGEST START");
        log.info(">>> INGEST: file={}, size={}KB",
                file.getOriginalFilename(), file.getSize() / 1024);

        Runtime rt = Runtime.getRuntime();
        long maxMB = rt.maxMemory() / 1024 / 1024;
        if (maxMB < 512) {
            throw new Exception(
                    "JVM Max Heap chi co " + maxMB + "MB! Can it nhat 1024MB. "
                            + "Vao Run > Edit Configurations > VM options: -Xms512m -Xmx4g");
        }

        validateFileType(file);

        System.gc();
        logHeap("AFTER GC");

        String text = extractText(file);
        log.info(">>> INGEST: extracted {} chars", text.length());
        logHeap("AFTER PDF PARSE");

        if (text.isBlank()) {
            throw new Exception("Khong doc duoc noi dung tu file.");
        }

        List<String> textChunks = chunkText(text);
        text = null;
        log.info(">>> INGEST: {} chunks", textChunks.size());

        Document doc = new Document();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setTotalChunks(textChunks.size());
        Document savedDoc = this.documentRepository.save(doc);
        logHeap("AFTER SAVE DOC");

        int embeddedCount = 0;
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setContent(chunkText);
            chunk.setChunkIndex(i);
            chunk.setDocument(savedDoc);

            logHeap("BEFORE EMBED chunk " + i);

            try {
                double[] emb = this.ollamaService.getEmbedding(chunkText);
                logHeap("AFTER EMBED chunk " + i + " (dim=" + emb.length + ")");

                if (emb.length > 0) {
                    chunk.setEmbedding(objectMapper.writeValueAsString(emb));
                    embeddedCount++;
                }
            } catch (Exception e) {
                log.warn(">>> INGEST: embed chunk {} failed: {}", i, e.getMessage());
            }

            this.documentChunkRepository.save(chunk);
            log.info(">>> INGEST: chunk {}/{} done", i + 1, textChunks.size());
        }

        logHeap("INGEST DONE");
        log.info(">>> INGEST DONE: embedded {}/{}", embeddedCount, textChunks.size());

        ResIngestDTO res = new ResIngestDTO();
        res.setDocumentId(savedDoc.getId());
        res.setFileName(savedDoc.getFileName());
        res.setTotalChunks(savedDoc.getTotalChunks());
        res.setMessage("Embedded " + embeddedCount + "/" + textChunks.size()
                + " chunks. MaxHeap=" + maxMB + "MB");
        res.setCreatedAt(savedDoc.getCreatedAt());
        return res;
    }

    private void logHeap(String phase) {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory() / 1024 / 1024;
        long total = rt.totalMemory() / 1024 / 1024;
        long free = rt.freeMemory() / 1024 / 1024;
        long used = total - free;
        log.info(">>> HEAP [{}]: used={}MB / max={}MB (free={}MB)",
                phase, used, max, free);
    }

    private String extractText(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        if ("application/pdf".equals(contentType) || fileName.endsWith(".pdf")) {
            byte[] bytes = file.getBytes();
            log.info(">>> PDF: reading {} KB", bytes.length / 1024);
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(pdf);
                log.info(">>> PDF: {} pages, {} chars",
                        pdf.getNumberOfPages(), text.length());
                return text;
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private void validateFileType(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        boolean isPdf = "application/pdf".equals(contentType) || fileName.endsWith(".pdf");
        boolean isText = (contentType != null && contentType.startsWith("text/"))
                || fileName.endsWith(".txt") || fileName.endsWith(".csv")
                || fileName.endsWith(".md");

        if (!isPdf && !isText) {
            throw new Exception("Chi ho tro PDF, TXT, CSV, MD. File type: " + contentType);
        }
    }

    @Override
    public List<VectorStoreService.SearchResult> retrieveContext(String query) {
        double[] queryEmb = this.ollamaService.getEmbedding(query);
        if (queryEmb.length == 0) {
            log.error(">>> RETRIEVE: embedding query failed");
            return new ArrayList<>();
        }
        List<VectorStoreService.SearchResult> results =
                this.vectorStoreService.search(queryEmb, topK, threshold);
        log.info(">>> RETRIEVE: {} results for \"{}\"",
                results.size(), query.substring(0, Math.min(50, query.length())));
        return results;
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        text = text.replaceAll("\\s+", " ").trim();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()) {
                int breakPos = findBreakPoint(text, start, end);
                if (breakPos > start) end = breakPos;
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);

            if (end >= text.length()) break;

            start = end - chunkOverlap;
            if (start < 0) start = 0;
        }
        return chunks;
    }

    private int findBreakPoint(String text, int start, int end) {
        for (int i = end; i > start + (end - start) / 2; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '!' || c == '?' || c == '\n') return i;
        }
        for (int i = end; i > start + (end - start) / 2; i--) {
            if (text.charAt(i - 1) == ' ') return i;
        }
        return end;
    }
}
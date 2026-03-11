package com.AI.RAG.AgenticRAG.entity;

import com.AI.RAG.AgenticRAG.entity.Document;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    private int chunkIndex;

    // embedding lưu JSON: "[0.1, 0.2, ...]"
    @Column(columnDefinition = "LONGTEXT")
    private String embedding;

    @ManyToOne
    @JoinColumn(name = "document_id")
    private Document document;
}
package com.yipeng.jobcopilot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages resume embeddings in pgvector.
 *
 * VectorStore is optional — when spring.ai.vectorstore.type=none,
 * all methods degrade gracefully (log and return early).
 * Enable pgvector in application.properties to activate full RAG capability.
 */
@Slf4j
@Service
public class ResumeEmbeddingService {

    private static final int CHUNK_SIZE    = 500;
    private static final int CHUNK_OVERLAP = 100;

    // Optional: only present when pgvector is configured
    @Autowired(required = false)
    private VectorStore vectorStore;

    public void embedResume(Long resumeId, Long userId, String text) {
        if (vectorStore == null) {
            log.info("VectorStore not configured — skipping embedding for resumeId={}", resumeId);
            return;
        }
        if (text == null || text.isBlank()) {
            log.warn("Skipping embedding for resumeId={}: text is empty", resumeId);
            return;
        }

        deleteResumeEmbeddings(resumeId);

        List<String> chunks = chunkText(text);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            documents.add(new Document(
                    chunks.get(i),
                    Map.of(
                            "resumeId",   resumeId.toString(),
                            "userId",     userId.toString(),
                            "type",       "resume",
                            "chunkIndex", String.valueOf(i)
                    )
            ));
        }

        vectorStore.add(documents);
        log.info("Embedded resumeId={} into {} chunks", resumeId, chunks.size());
    }

    public void deleteResumeEmbeddings(Long resumeId) {
        if (vectorStore == null) return;
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("resumeId", resumeId.toString()).build());
            log.info("Deleted existing embeddings for resumeId={}", resumeId);
        } catch (Exception e) {
            log.warn("Could not delete embeddings for resumeId={}: {}", resumeId, e.getMessage());
        }
    }

    public List<Document> searchResumeChunks(String query, Long resumeId, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore not configured — cannot search chunks");
            return List.of();
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(b.eq("resumeId", resumeId.toString()).build())
                .build();
        return vectorStore.similaritySearch(request);
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }
        return chunks;
    }
}
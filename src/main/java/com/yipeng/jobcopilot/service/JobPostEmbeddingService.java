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
 * Manages job post (JD) embeddings in pgvector.
 *
 * Mirrors ResumeEmbeddingService — same chunking strategy,
 * different metadata type ("jobpost" vs "resume").
 *
 * Enables:
 *   1. Token-efficient RAG: inject only the most relevant JD sections
 *      into prompts instead of the full JD text
 *   2. Cross-JD search: "which of my saved JDs best fits my resume?"
 */
@Slf4j
@Service
public class JobPostEmbeddingService {

    private static final int CHUNK_SIZE    = 500;
    private static final int CHUNK_OVERLAP = 100;

    @Autowired(required = false)
    private VectorStore vectorStore;

    public void embedJobPost(Long jobPostId, Long userId, String text) {
        if (vectorStore == null) {
            log.info("VectorStore not configured — skipping embedding for jobPostId={}", jobPostId);
            return;
        }
        if (text == null || text.isBlank()) {
            log.warn("Skipping embedding for jobPostId={}: description is empty", jobPostId);
            return;
        }

        deleteJobPostEmbeddings(jobPostId);

        List<String> chunks = chunkText(text);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            documents.add(new Document(
                    chunks.get(i),
                    Map.of(
                            "jobPostId",  jobPostId.toString(),
                            "userId",     userId.toString(),
                            "type",       "jobpost",
                            "chunkIndex", String.valueOf(i)
                    )
            ));
        }

        vectorStore.add(documents);
        log.info("Embedded jobPostId={} into {} chunks", jobPostId, chunks.size());
    }

    public void deleteJobPostEmbeddings(Long jobPostId) {
        if (vectorStore == null) return;
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("jobPostId", jobPostId.toString()).build());
            log.info("Deleted existing embeddings for jobPostId={}", jobPostId);
        } catch (Exception e) {
            log.warn("Could not delete embeddings for jobPostId={}: {}", jobPostId, e.getMessage());
        }
    }

    public List<Document> searchJobPostChunks(String query, Long jobPostId, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore not configured — cannot search JD chunks");
            return List.of();
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(b.eq("jobPostId", jobPostId.toString()).build())
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * Search across ALL job posts for a given user.
     * Used for cross-JD recommendation: "which JD best matches my resume?"
     */
    public List<Document> searchAcrossUserJobPosts(String query, Long userId, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore not configured — cannot search across JDs");
            return List.of();
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(
                        b.and(
                                b.eq("userId", userId.toString()),
                                b.eq("type", "jobpost")
                        ).build()
                )
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
package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.JobPostRecommendationResponse;
import com.yipeng.jobcopilot.dto.JobPostRecommendationResponse.RankedJobPost;
import com.yipeng.jobcopilot.entity.JobPost;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.exception.ResumeNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.JobPostRepository;
import com.yipeng.jobcopilot.repository.ResumeRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostRecommendationService {

    // How many JD chunks to retrieve across all job posts
    private static final int CROSS_JD_TOP_K = 20;

    // Minimum similarity score to be included in results
    private static final double MIN_SCORE = 0.2;

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final JobPostEmbeddingService jobPostEmbeddingService;
    private final ResumeEmbeddingService resumeEmbeddingService;

    /**
     * Recommends the user's saved job posts ranked by semantic similarity to their resume.
     *
     * Strategy:
     *   1. Use the resume content as the search query against all jobpost chunks
     *   2. Group chunks by jobPostId and average their similarity scores
     *   3. Filter out JDs with no embedding yet (warn user)
     *   4. Return ranked list with a one-line match reason per JD
     */
    public JobPostRecommendationResponse recommend(String email, Long resumeId) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));

        // Step 1: build a query from the resume's key content
        // Use skills + experience keywords as the search query
        String searchQuery = buildSearchQuery(resume);
        log.debug("JD recommendation query for resumeId={}: [{}]", resumeId, searchQuery);

        // Step 2: search across all this user's jobpost chunks
        List<Document> chunks = jobPostEmbeddingService.searchAcrossUserJobPosts(
                searchQuery, user.getId(), CROSS_JD_TOP_K);

        if (chunks.isEmpty()) {
            log.warn("No JD embeddings found for userId={}. " +
                    "Have job posts been embedded?", user.getId());
            return JobPostRecommendationResponse.builder()
                    .resumeId(resumeId)
                    .resumeTitle(resume.getTitle())
                    .recommendedJobPosts(List.of())
                    .build();
        }

        // Step 3: group chunks by jobPostId, compute average score per JD
        // Document score is in metadata under "distance" — Spring AI pgvector
        // returns cosine distance (lower = more similar), we convert to similarity
        Map<Long, List<Double>> scoresByJobPost = new LinkedHashMap<>();

        for (Document chunk : chunks) {
            String jobPostIdStr = chunk.getMetadata().get("jobPostId") != null
                    ? chunk.getMetadata().get("jobPostId").toString()
                    : null;

            if (jobPostIdStr == null) continue;

            try {
                Long jobPostId = Long.parseLong(jobPostIdStr);
                double score = extractScore(chunk);
                scoresByJobPost.computeIfAbsent(jobPostId, k -> new ArrayList<>()).add(score);
            } catch (NumberFormatException e) {
                log.warn("Could not parse jobPostId from chunk metadata: {}", jobPostIdStr);
            }
        }

        // Step 4: fetch JobPost details and build ranked response
        List<RankedJobPost> ranked = scoresByJobPost.entrySet().stream()
                .map(entry -> {
                    Long jobPostId = entry.getKey();
                    double avgScore = entry.getValue().stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0.0);

                    // Round to 2 decimal places
                    avgScore = Math.round(avgScore * 100.0) / 100.0;

                    return Map.entry(jobPostId, avgScore);
                })
                .filter(e -> e.getValue() >= MIN_SCORE)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(e -> buildRankedJobPost(e.getKey(), e.getValue(), user.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("JD recommendation for resumeId={}: {} JDs ranked", resumeId, ranked.size());

        return JobPostRecommendationResponse.builder()
                .resumeId(resumeId)
                .resumeTitle(resume.getTitle())
                .recommendedJobPosts(ranked)
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds a concise search query from the resume.
     * Uses the first 800 chars of resume content — enough to capture
     * skills and experience keywords without blowing up the embedding call.
     */
    private String buildSearchQuery(Resume resume) {
        String content = resume.getContent();
        if (content == null || content.isBlank()) {
            return resume.getTitle();
        }
        // Take up to 800 characters — covers skills + first experience entry typically
        return content.length() > 800 ? content.substring(0, 800) : content;
    }

    /**
     * Extracts similarity score from a Document.
     * Spring AI stores the cosine distance in metadata under "distance" key.
     * similarity = 1 - distance (cosine distance range: 0.0 to 2.0, typically 0.0-1.0)
     */
    private double extractScore(Document chunk) {
        Object distanceObj = chunk.getMetadata().get("distance");
        if (distanceObj instanceof Number) {
            double distance = ((Number) distanceObj).doubleValue();
            return Math.max(0.0, 1.0 - distance);
        }
        // If no distance metadata, default to a neutral mid-range score
        return 0.5;
    }

    /**
     * Fetches the JobPost from DB and builds a RankedJobPost DTO.
     * Returns null if the job post no longer exists (was deleted after embedding).
     */
    private RankedJobPost buildRankedJobPost(Long jobPostId, double score, Long userId) {
        return jobPostRepository.findByIdAndUserId(jobPostId, userId)
                .map(jp -> RankedJobPost.builder()
                        .jobPostId(jp.getId())
                        .companyName(jp.getCompanyName())
                        .jobTitle(jp.getJobTitle())
                        .similarityScore(score)
                        .matchReason(buildMatchReason(score, jp))
                        .build())
                .orElse(null);
    }

    /**
     * Generates a one-line human-readable explanation of the match score.
     */
    private String buildMatchReason(double score, JobPost jp) {
        if (score >= 0.40) {
            return "Strong match — your background aligns well with the core requirements at "
                    + jp.getCompanyName() + ".";
        } else if (score >= 0.33) {
            return "Good match — several of your skills overlap with what "
                    + jp.getCompanyName() + " is looking for.";
        } else if (score >= 0.25) {
            return "Partial match — some relevant experience, but gaps exist for this role at "
                    + jp.getCompanyName() + ".";
        } else {
            return "Low match — limited overlap between your profile and "
                    + jp.getCompanyName() + "'s requirements.";
        }
    }
}
package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.AiMatchAnalysisResponse;
import com.yipeng.jobcopilot.dto.AiSkillExtractionResult;
import com.yipeng.jobcopilot.dto.MatchAnalysisResponse;
import com.yipeng.jobcopilot.entity.JobPost;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.enumeration.SkillTier;
import com.yipeng.jobcopilot.exception.JobPostNotFoundException;
import com.yipeng.jobcopilot.exception.ResumeNotFoundException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.JobPostRepository;
import com.yipeng.jobcopilot.repository.ResumeRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostRepository jobPostRepository;
    private final JobSkillExtractorService jobSkillExtractorService;
    private final AiSkillExtractorService aiSkillExtractorService;
    private final MatchResultService matchResultService;
    private final OpenAiService openAiService;

    // ── /match — main analysis endpoint ───────────────────────────────────────
    // @Transactional (not readOnly) because we persist the result at the end

    @Transactional
    public MatchAnalysisResponse analyzeMatch(String email, Long resumeId, Long jobPostId) {
        User user = getUser(email);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found or not owned by user"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found or not owned by user"));

        String resumeText = concat(resume.getTitle(), resume.getCandidateName(),
                resume.getEmail(), resume.getContent());
        Set<String> resumeSkills = jobSkillExtractorService.extractSkills(resumeText);

        String jdText = concat(jobPost.getCompanyName(), jobPost.getJobTitle(),
                jobPost.getDescription(), jobPost.getEmploymentType(), jobPost.getLocation());

        MatchAnalysisResponse response = tryAiAnalysis(resume, jobPost, resumeSkills, jdText);

        // Persist the result so /results/me can show history
        // fallbackUsed = true when AI extraction was not used
        matchResultService.saveMatchResult(
                user, resume, jobPost,
                response.getWeightedMatchScore(),
                response.getSummary(),
                !response.isAiExtractionUsed()
        );

        return response;
    }

    // ── /match-ai — legacy endpoint logic (moved out of Controller) ───────────
    // Kept for backwards compatibility. Uses OpenAiService for holistic AI scoring
    // rather than structured skill extraction.

    @Transactional
    public AiMatchAnalysisResponse analyzeMatchWithAi(String email, Long resumeId,
                                                      Long jobPostId, boolean debugMode) {
        User user = getUser(email);

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found or not owned by user"));

        JobPost jobPost = jobPostRepository.findByIdAndUserId(jobPostId, user.getId())
                .orElseThrow(() -> new JobPostNotFoundException("Job post not found or not owned by user"));

        try {
            Map<String, Object> aiResult = openAiService.analyzeMatch(
                    resume.getContent(), jobPost.getDescription());

            int matchScore  = (Integer) aiResult.get("matchScore");
            String aiSummary = (String) aiResult.get("aiSummary");

            matchResultService.saveMatchResult(user, resume, jobPost, matchScore, aiSummary, false);

            return AiMatchAnalysisResponse.builder()
                    .resumeId(resume.getId())
                    .jobPostId(jobPost.getId())
                    .matchScore(matchScore)
                    .aiSummary(aiSummary)
                    .fallbackUsed(false)
                    .rawAiResponse(debugMode ? (String) aiResult.get("rawAiResponse") : null)
                    .build();

        } catch (Exception ex) {
            log.warn("OpenAiService.analyzeMatch failed for jobPost={}, using fallback. Reason: {}",
                    jobPost.getId(), ex.getMessage());

            // Fallback: run the new structured analysis as the fallback result
            MatchAnalysisResponse fallback = tryAiAnalysis(
                    resume, jobPost,
                    jobSkillExtractorService.extractSkills(
                            concat(resume.getTitle(), resume.getCandidateName(),
                                    resume.getEmail(), resume.getContent())),
                    concat(jobPost.getCompanyName(), jobPost.getJobTitle(),
                            jobPost.getDescription(), jobPost.getEmploymentType(), jobPost.getLocation())
            );

            matchResultService.saveMatchResult(
                    user, resume, jobPost,
                    fallback.getWeightedMatchScore(),
                    fallback.getSummary(),
                    true
            );

            return AiMatchAnalysisResponse.builder()
                    .resumeId(resume.getId())
                    .jobPostId(jobPost.getId())
                    .matchScore(fallback.getWeightedMatchScore())
                    .aiSummary(fallback.getSummary())
                    .fallbackUsed(true)
                    .fallbackReason(debugMode ? ex.getMessage()
                            : "AI service is temporarily unavailable. Rule-based analysis was used instead.")
                    .matchedSkills(fallback.getMatchedSkills())
                    .missingSkills(fallback.getMissingSkills())
                    .build();
        }
    }

    // ── AI extraction path ─────────────────────────────────────────────────────

    private MatchAnalysisResponse tryAiAnalysis(Resume resume, JobPost jobPost,
                                                Set<String> resumeSkills, String jdText) {
        try {
            AiSkillExtractionResult aiResult = aiSkillExtractorService.extractSkillsFromJd(jdText);
            log.info("AI skill extraction succeeded for jobPost={}", jobPost.getId());

            List<String> required  = aiResult.requiredSkills();
            List<String> preferred = aiResult.preferredSkills();

            boolean scoringOnPreferred = required.isEmpty() && !preferred.isEmpty();
            List<String> scoringSkills = scoringOnPreferred ? preferred : required;

            return buildResponse(resume, jobPost, resumeSkills,
                    scoringSkills, preferred, scoringOnPreferred, true);

        } catch (Exception ex) {
            log.warn("AI skill extraction failed for jobPost={}, falling back to keyword matching. Reason: {}",
                    jobPost.getId(), ex.getMessage());
            return keywordFallback(resume, jobPost, resumeSkills, jdText);
        }
    }

    private MatchAnalysisResponse keywordFallback(Resume resume, JobPost jobPost,
                                                  Set<String> resumeSkills, String jdText) {
        Set<String> jobSkills = jobSkillExtractorService.extractSkills(jdText);
        return buildResponse(resume, jobPost, resumeSkills,
                new ArrayList<>(jobSkills), List.of(), false, false);
    }

    // ── Core scoring logic ─────────────────────────────────────────────────────

    private MatchAnalysisResponse buildResponse(Resume resume,
                                                JobPost jobPost,
                                                Set<String> resumeSkills,
                                                List<String> scoringSkills,
                                                List<String> preferredSkills,
                                                boolean scoringOnPreferred,
                                                boolean aiExtractionUsed) {
        List<String> matchedSkills     = new ArrayList<>();
        List<String> missingSkills     = new ArrayList<>();
        List<String> coreMissingSkills = new ArrayList<>();

        for (String skill : scoringSkills) {
            if (resumeSkills.contains(skill)) {
                matchedSkills.add(skill);
            } else {
                missingSkills.add(skill);
                if (jobSkillExtractorService.getTier(skill) == SkillTier.CORE) {
                    coreMissingSkills.add(skill);
                }
            }
        }

        List<String> preferredMatched = List.of();
        List<String> preferredMissing = List.of();
        if (!scoringOnPreferred && !preferredSkills.isEmpty()) {
            preferredMatched = preferredSkills.stream()
                    .filter(resumeSkills::contains).toList();
            preferredMissing = preferredSkills.stream()
                    .filter(s -> !resumeSkills.contains(s)).toList();
        }

        int total = scoringSkills.size();
        int matchScore = total == 0 ? 0
                : (int) Math.round((double) matchedSkills.size() / total * 100);

        int totalWeight = scoringSkills.stream()
                .mapToInt(jobSkillExtractorService::getWeight).sum();
        int matchedWeight = matchedSkills.stream()
                .mapToInt(jobSkillExtractorService::getWeight).sum();
        int weightedMatchScore = totalWeight == 0 ? 0
                : (int) Math.round((double) matchedWeight / totalWeight * 100);

        String summary = buildSummary(weightedMatchScore, matchedSkills,
                missingSkills, coreMissingSkills, aiExtractionUsed, scoringOnPreferred);

        return MatchAnalysisResponse.builder()
                .resumeId(resume.getId())
                .jobPostId(jobPost.getId())
                .matchScore(matchScore)
                .weightedMatchScore(weightedMatchScore)
                .matchedSkills(matchedSkills)
                .missingSkills(missingSkills)
                .coreMissingSkills(coreMissingSkills)
                .preferredSkillsMatched(preferredMatched)
                .preferredSkillsMissing(preferredMissing)
                .aiExtractionUsed(aiExtractionUsed)
                .summary(summary)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private String concat(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) sb.append(p).append(' ');
        }
        return sb.toString();
    }

    private String buildSummary(int score, List<String> matched, List<String> missing,
                                List<String> coreMissing, boolean aiUsed,
                                boolean scoringOnPreferred) {
        String method = aiUsed ? "AI-powered" : "keyword-based";
        String basis  = scoringOnPreferred
                ? "No hard requirements identified — scored on preferred skills"
                : "scored on required skills";

        if (missing.isEmpty() && !matched.isEmpty()) {
            return String.format("Strong match (%s, %s). Weighted score: %d%%.",
                    method, basis, score);
        }
        if (matched.isEmpty()) {
            return String.format("Weak match (%s, %s). Weighted score: %d%%.",
                    method, basis, score);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Partial match (%s, %s). Weighted score: %d%%. ", method, basis, score));
        sb.append("Matched: ").append(matched).append(". ");
        sb.append("Missing: ").append(missing).append(".");
        if (!coreMissing.isEmpty()) {
            sb.append(" ⚠ Core skill gaps: ").append(coreMissing).append(".");
        }
        return sb.toString();
    }
}
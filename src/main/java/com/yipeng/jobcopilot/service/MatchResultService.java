package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.MatchResultResponse;
import com.yipeng.jobcopilot.entity.JobPost;
import com.yipeng.jobcopilot.entity.MatchResult;
import com.yipeng.jobcopilot.entity.Resume;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.MatchResultRepository;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchResultService {

    private final MatchResultRepository matchResultRepository;
    private final UserRepository userRepository;

    public MatchResultResponse saveMatchResult(
            User user,
            Resume resume,
            JobPost jobPost,
            int matchScore,
            String aiSummary,
            boolean fallbackUsed
    ) {
        MatchResult matchResult = MatchResult.builder()
                .user(user)
                .resume(resume)
                .jobPost(jobPost)
                .matchScore(matchScore)
                .aiSummary(aiSummary)
                .fallbackUsed(fallbackUsed)
                .build();

        return toResponse(matchResultRepository.save(matchResult));
    }

    @Transactional(readOnly = true)
    public List<MatchResultResponse> getMyMatchResults(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return matchResultRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MatchResultResponse toResponse(MatchResult matchResult) {
        Resume resume = matchResult.getResume();
        JobPost jobPost = matchResult.getJobPost();

        return MatchResultResponse.builder()
                .id(matchResult.getId())
                .resumeId(resume.getId())
                .resumeTitle(resume.getTitle())
                .jobPostId(jobPost.getId())
                .companyName(jobPost.getCompanyName())
                .jobTitle(jobPost.getJobTitle())
                .matchScore(matchResult.getMatchScore())
                .aiSummary(matchResult.getAiSummary())
                .fallbackUsed(matchResult.isFallbackUsed())
                .createdAt(matchResult.getCreatedAt())
                .build();
    }
}
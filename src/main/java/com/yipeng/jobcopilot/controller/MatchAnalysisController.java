package com.yipeng.jobcopilot.controller;

import com.yipeng.jobcopilot.dto.*;
import com.yipeng.jobcopilot.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class MatchAnalysisController {

    private final MatchAnalysisService matchAnalysisService;
    private final MatchResultService matchResultService;
    private final ResumeSuggestionService resumeSuggestionService;
    private final CoverLetterService coverLetterService;
    private final MockInterviewService mockInterviewService;
    private final SelfIntroService selfIntroService;

    @PostMapping("/match")
    public ApiResponse<MatchAnalysisResponse> analyzeMatch(
            Authentication authentication,
            @Valid @RequestBody MatchAnalysisRequest request
    ) {
        MatchAnalysisResponse response = matchAnalysisService.analyzeMatch(
                authentication.getName(),
                request.getResumeId(),
                request.getJobPostId()
        );
        return ApiResponse.success("Match analysis completed successfully", response);
    }

    /**
     * @deprecated Use /match instead.
     */
    @Deprecated
    @PostMapping("/match-ai")
    public ApiResponse<AiMatchAnalysisResponse> analyzeMatchWithAi(
            Authentication authentication,
            @Valid @RequestBody MatchAnalysisRequest request
    ) {
        boolean debugMode = Boolean.parseBoolean(
                System.getenv().getOrDefault("AI_DEBUG", "false"));

        AiMatchAnalysisResponse response = matchAnalysisService.analyzeMatchWithAi(
                authentication.getName(),
                request.getResumeId(),
                request.getJobPostId(),
                debugMode
        );

        String message = response.getFallbackUsed()
                ? "AI service unavailable. Fallback match analysis completed successfully"
                : "AI match analysis completed successfully";

        return ApiResponse.success(message, response);
    }

    @PostMapping("/suggestions")
    public ApiResponse<ResumeSuggestionResponse> getSuggestions(
            Authentication authentication,
            @Valid @RequestBody MatchAnalysisRequest request
    ) {
        ResumeSuggestionResponse response = resumeSuggestionService.getSuggestions(
                authentication.getName(),
                request.getResumeId(),
                request.getJobPostId()
        );
        return ApiResponse.success("Resume suggestions generated successfully", response);
    }

    @GetMapping("/results/me")
    public ApiResponse<List<MatchResultResponse>> getMyMatchResults(Authentication authentication) {
        List<MatchResultResponse> results = matchResultService.getMyMatchResults(
                authentication.getName());
        return ApiResponse.success("Match results fetched successfully", results);
    }

    @PostMapping("/cover-letter")
    public ApiResponse<CoverLetterResponse> generateCoverLetter(
            Authentication authentication,
            @Valid @RequestBody CoverLetterRequest request
    ) {
        CoverLetterResponse response = coverLetterService.generate(
                authentication.getName(),
                request.getResumeId(),
                request.getJobPostId(),
                request.getExtraContext()
        );
        return ApiResponse.success("Cover letter generated successfully", response);
    }

    @PostMapping("/mock-interview")
    public ApiResponse<MockInterviewResponse> generateMockInterview(
            Authentication authentication,
            @Valid @RequestBody MockInterviewRequest request
    ) {
        MockInterviewResponse response = mockInterviewService.generate(
                authentication.getName(),
                request.getResumeId(),
                request.getJobPostId(),
                request.getFocusArea()
        );
        return ApiResponse.success("Mock interview questions generated successfully", response);
    }

    @PostMapping("/self-intro")
    public ApiResponse<SelfIntroResponse> generateSelfIntro(
            Authentication authentication,
            @Valid @RequestBody SelfIntroRequest request
    ) {
        SelfIntroResponse response = selfIntroService.generate(
                authentication.getName(),
                request.getResumeId(),
                request.getJobPostId()
        );
        return ApiResponse.success("Self-introduction generated successfully", response);
    }
}
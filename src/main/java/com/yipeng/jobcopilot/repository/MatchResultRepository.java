package com.yipeng.jobcopilot.repository;

import com.yipeng.jobcopilot.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    List<MatchResult> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 查某个用户对某份简历+某个JD的最新一次分析结果
    Optional<MatchResult> findTopByResumeIdAndJobPostIdOrderByCreatedAtDesc(
            Long resumeId, Long jobPostId);
}
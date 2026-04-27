package com.yipeng.jobcopilot.repository;

import com.yipeng.jobcopilot.entity.JobPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostRepository extends JpaRepository<JobPost, Long> {

    List<JobPost> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<JobPost> findByIdAndUserId(Long id, Long userId);

    List<JobPost> findByUserIdAndCompanyNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Long userId,
            String companyName
    );

    List<JobPost> findByUserIdAndJobTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            Long userId,
            String jobTitle
    );
}
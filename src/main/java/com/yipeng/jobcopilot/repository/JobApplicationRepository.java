package com.yipeng.jobcopilot.repository;

import com.yipeng.jobcopilot.entity.JobApplication;
import com.yipeng.jobcopilot.enumeration.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<JobApplication> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            ApplicationStatus status
    );

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);
}
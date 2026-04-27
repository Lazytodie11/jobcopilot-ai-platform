package com.yipeng.jobcopilot.repository;

import com.yipeng.jobcopilot.entity.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByEmail(String email);

    List<Resume> findByTitleContainingIgnoreCase(String keyword);

    Page<Resume> findAll(Pageable pageable);

    List<Resume> findByUserId(Long userId);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);
}
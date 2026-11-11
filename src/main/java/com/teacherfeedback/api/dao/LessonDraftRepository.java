package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.LessonDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LessonDraftRepository extends JpaRepository<LessonDraft, Long> {
    Optional<LessonDraft> findByStudentId(Long studentId);
    void deleteByStudentId(Long studentId);
}

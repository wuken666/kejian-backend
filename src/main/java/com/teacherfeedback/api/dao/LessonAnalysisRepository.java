package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.LessonAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface LessonAnalysisRepository extends JpaRepository<LessonAnalysis, Long> {
    Optional<LessonAnalysis> findByIdAndStudentId(Long id, Long studentId);

    List<LessonAnalysis> findTop5ByStudentIdAndOcrTextAndFeedbackToneAndFeedbackLengthAndConfirmedAtIsNullOrderByCreatedAtDesc(
        Long studentId,
        String ocrText,
        String feedbackTone,
        String feedbackLength
    );
}

package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByLessonRecordStudentIdOrderByCreatedAtDesc(Long studentId);
    Optional<Feedback> findByIdAndLessonRecordStudentId(Long id, Long studentId);
    Optional<Feedback> findFirstByLessonRecordStudentIdOrderByCreatedAtDesc(Long studentId);
}

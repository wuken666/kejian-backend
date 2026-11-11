package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.AsrEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsrEvaluationRepository extends JpaRepository<AsrEvaluation, Long> {
    List<AsrEvaluation> findTop20ByOrderByCreatedAtDesc();
    List<AsrEvaluation> findTop20ByStudentTeacherIdOrderByCreatedAtDesc(Long teacherId);
}

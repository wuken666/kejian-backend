package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.GrowthReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrowthReportRepository extends JpaRepository<GrowthReport, Long> {
    List<GrowthReport> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}

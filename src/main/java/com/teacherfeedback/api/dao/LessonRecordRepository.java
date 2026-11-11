package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.LessonRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface LessonRecordRepository extends JpaRepository<LessonRecord, Long> {
    List<LessonRecord> findByStudentTeacherIdAndLessonDateBetween(Long teacherId, LocalDate from, LocalDate to);

    List<LessonRecord> findByStudentIdOrderByLessonDateDescIdDesc(Long studentId);

    List<LessonRecord> findTop3ByStudentIdOrderByLessonDateDescIdDesc(Long studentId);

    List<LessonRecord> findTop8ByStudentIdOrderByLessonDateDescIdDesc(Long studentId);

    Optional<LessonRecord> findByIdAndStudentId(Long id, Long studentId);
}

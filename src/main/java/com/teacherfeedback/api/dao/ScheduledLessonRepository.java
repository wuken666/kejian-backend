package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.ScheduledLesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduledLessonRepository extends JpaRepository<ScheduledLesson, Long> {
    List<ScheduledLesson> findByTeacherIdAndStatusAndLessonDateBeforeOrderByLessonDateAscStartTimeAsc(
        Long teacherId, ScheduledLesson.Status status, LocalDate date);
    List<ScheduledLesson> findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
        Long teacherId, LocalDate from, LocalDate to
    );

    List<ScheduledLesson> findByTeacherIdAndLessonDateOrderByStartTimeAsc(Long teacherId, LocalDate date);

    Optional<ScheduledLesson> findByIdAndTeacherId(Long id, Long teacherId);
}

package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.LessonSession;
import com.teacherfeedback.api.model.LessonSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface LessonSessionRepository extends JpaRepository<LessonSession, Long> {
    Optional<LessonSession> findFirstByStatusInOrderByUpdatedAtDesc(Collection<LessonSessionStatus> statuses);

    Optional<LessonSession> findFirstByStudentTeacherIdAndStatusInOrderByUpdatedAtDesc(
        Long teacherId,
        Collection<LessonSessionStatus> statuses
    );
}

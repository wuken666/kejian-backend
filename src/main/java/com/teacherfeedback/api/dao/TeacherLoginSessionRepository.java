package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.TeacherLoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TeacherLoginSessionRepository extends JpaRepository<TeacherLoginSession, Long> {
    Optional<TeacherLoginSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
        String tokenHash,
        LocalDateTime now
    );
}

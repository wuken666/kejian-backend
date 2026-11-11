package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
}

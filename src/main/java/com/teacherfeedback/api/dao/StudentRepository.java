package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findAllByTeacherId(Long teacherId);
}

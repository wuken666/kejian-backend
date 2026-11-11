package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.StudentHotword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentHotwordRepository extends JpaRepository<StudentHotword, Long> {
    List<StudentHotword> findByStudentIdOrderByTermAsc(Long studentId);
    void deleteByStudentId(Long studentId);
}

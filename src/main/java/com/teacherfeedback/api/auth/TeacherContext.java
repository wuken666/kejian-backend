package com.teacherfeedback.api.auth;

import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.model.Student;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

public final class TeacherContext {
    private static final ThreadLocal<Long> CURRENT_TEACHER = new ThreadLocal<>();

    private TeacherContext() {
    }

    public static void set(Long teacherId) {
        CURRENT_TEACHER.set(teacherId);
    }

    public static void clear() {
        CURRENT_TEACHER.remove();
    }

    public static Long currentTeacherId() {
        return CURRENT_TEACHER.get();
    }

    public static boolean hasCurrentTeacher() {
        return CURRENT_TEACHER.get() != null;
    }

    // 统一校验学生归属；没有经过 HTTP 过滤器的单元测试保持原有直接调用行为。
    public static Student requireStudent(StudentRepository repository, Long studentId) {
        Student student = repository.findById(studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "学生档案不存在"));
        assertStudentAccess(student);
        return student;
    }

    public static void assertStudentAccess(Student student) {
        Long currentTeacherId = currentTeacherId();
        if (currentTeacherId != null && !currentTeacherId.equals(student.getTeacherId())) {
            throw new ResponseStatusException(NOT_FOUND, "学生档案不存在");
        }
    }

    public static Optional<Long> filterTeacherId() {
        return Optional.ofNullable(currentTeacherId());
    }
}

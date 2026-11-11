package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dao.LessonSessionRepository;
import com.teacherfeedback.api.dao.LessonSessionSegmentRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.model.LessonSession;
import com.teacherfeedback.api.model.LessonSessionSegment;
import com.teacherfeedback.api.model.Student;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = "auth.require-teacher-token=false")
@Transactional
class LessonSessionDeletionIntegrationTests {
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private LessonSessionRepository lessonSessionRepository;
    @Autowired
    private LessonSessionSegmentRepository lessonSessionSegmentRepository;
    @Autowired
    private LessonSessionService lessonSessionService;
    @Autowired
    private EntityManager entityManager;

    // 验证存在已保存语音片段时，删除课堂会话也能正常提交事务。
    @Test
    void deletesSessionWithPersistedSegmentsWithoutLeavingTransientReferences() {
        Student student = studentRepository.save(new Student("删除测试", "初二", "英语", ""));
        LessonSession session = lessonSessionRepository.save(new LessonSession(student, "英语课堂"));
        lessonSessionSegmentRepository.saveAndFlush(
            new LessonSessionSegment(session, "delete-test-segment", 0, 12, "测试课堂转写")
        );

        assertDoesNotThrow(() -> {
            lessonSessionService.delete(session.getId());
            entityManager.flush();
        });
        assertFalse(lessonSessionRepository.existsById(session.getId()));
    }
}

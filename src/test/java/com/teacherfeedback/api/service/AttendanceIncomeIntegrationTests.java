package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.ScheduledLessonRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.model.ScheduledLesson;
import com.teacherfeedback.api.model.Student;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "auth.require-teacher-token=false")
@Transactional
class AttendanceIncomeIntegrationTests {
    @Autowired StudentRepository students;
    @Autowired ScheduledLessonRepository lessons;
    @Autowired ScheduleService schedule;
    @Autowired TeachingInsightsService insights;
    @Autowired EntityManager entities;

    @AfterEach void cleanup() { TeacherContext.clear(); }

    @Test void persistsExactSnapshotAndKeepsTeacherIncomeIsolated() {
        TeacherContext.set(1L);
        LocalDate date = LocalDate.of(2088, 5, 1);
        Student student = new Student(1L, "income-test", "初二", "英语", "");
        student.setLessonFee(new BigDecimal("150.25"));
        students.saveAndFlush(student);
        ScheduledLesson lesson = lessons.saveAndFlush(new ScheduledLesson(student, date, LocalTime.of(9, 0), 90, "income-test"));
        Long studentId = student.getId();
        Long lessonId = lesson.getId();
        schedule.status(lessonId, ScheduledLesson.Status.COMPLETED);
        entities.flush();
        entities.clear();
        assertEquals(new BigDecimal("150.25"), lessons.findById(lessonId).orElseThrow().getAttendanceIncome());
        students.findById(studentId).orElseThrow().setLessonFee(new BigDecimal("250.50"));
        entities.flush();
        entities.clear();
        schedule.status(lessonId, ScheduledLesson.Status.COMPLETED);
        var row = insights.report(date, date).income().students().stream()
            .filter(item -> item.studentId().equals(studentId)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("150.25"), row.amount());
        assertEquals(1, row.lessons());
        TeacherContext.set(Long.MAX_VALUE);
        assertEquals(new BigDecimal("0.00"), insights.report(date, date).income().total());
        TeacherContext.set(1L);
        schedule.status(lessonId, ScheduledLesson.Status.SCHEDULED);
        entities.flush();
        entities.clear();
        assertNull(lessons.findById(lessonId).orElseThrow().getAttendanceIncome());
        schedule.status(lessonId, ScheduledLesson.Status.COMPLETED);
        entities.flush();
        entities.clear();
        assertEquals(new BigDecimal("250.50"), lessons.findById(lessonId).orElseThrow().getAttendanceIncome());
    }
}

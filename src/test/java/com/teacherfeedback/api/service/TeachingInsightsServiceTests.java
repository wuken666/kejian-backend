package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.ScheduledLessonRepository;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.ScheduledLesson;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeachingInsightsServiceTests {
    private final ScheduledLessonRepository schedules = mock(ScheduledLessonRepository.class);
    private final LessonRecordRepository records = mock(LessonRecordRepository.class);
    private final TeachingInsightsService service = new TeachingInsightsService(schedules, records);
    private final LocalDate from = LocalDate.of(2026, 11, 1);
    private final LocalDate to = LocalDate.of(2026, 11, 30);

    @AfterEach void cleanup() { TeacherContext.clear(); }

    @Test void snapshotsIncomeAtAttendanceAndUndoThenRecheckUsesNewFee() {
        Student student = mock(Student.class);
        when(student.getTeacherId()).thenReturn(1L);
        when(student.getId()).thenReturn(7L);
        when(student.getName()).thenReturn("小雨");
        when(student.getLessonFee()).thenReturn(new BigDecimal("200.10"));
        var lesson = lesson(student, 90, ScheduledLesson.Status.SCHEDULED);
        when(schedules.findByIdAndTeacherId(9L, 1L)).thenReturn(java.util.Optional.of(lesson));
        when(schedules.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(1L, from, to)).thenReturn(List.of(lesson));
        when(records.findByStudentTeacherIdAndLessonDateBetween(1L, from, to)).thenReturn(List.of());
        var schedule = new ScheduleService(schedules, mock(com.teacherfeedback.api.dao.StudentRepository.class));
        schedule.status(9L, ScheduledLesson.Status.COMPLETED);
        assertEquals(new BigDecimal("200.10"), service.report(from, to).income().total());
        assertEquals(new BigDecimal("133.40"), service.report(from, to).income().averagePerHour());
        when(student.getLessonFee()).thenReturn(new BigDecimal("300.20"));
        schedule.status(9L, ScheduledLesson.Status.COMPLETED);
        assertEquals(new BigDecimal("200.10"), service.report(from, to).income().total());
        assertEquals(1, service.report(from, to).income().pricedLessons());
        schedule.status(9L, ScheduledLesson.Status.SCHEDULED);
        assertEquals(new BigDecimal("0.00"), service.report(from, to).income().total());
        assertNull(lesson.getAttendanceIncome());
        schedule.status(9L, ScheduledLesson.Status.COMPLETED);
        assertEquals(new BigDecimal("300.20"), service.report(from, to).income().total());
    }

    @Test void distinguishesUnpricedAndFreeClassesWithoutBackfillingHistory() {
        Student student = mock(Student.class);
        when(student.getTeacherId()).thenReturn(1L);
        when(student.getId()).thenReturn(7L);
        when(student.getName()).thenReturn("小雨");
        var unpriced = lesson(student, 90, ScheduledLesson.Status.COMPLETED);
        when(student.getLessonFee()).thenReturn(new BigDecimal("100.10"));
        var paid = lesson(student, 60, ScheduledLesson.Status.COMPLETED);
        when(student.getLessonFee()).thenReturn(new BigDecimal("200.20"));
        var paidAgain = lesson(student, 60, ScheduledLesson.Status.COMPLETED);
        when(student.getLessonFee()).thenReturn(BigDecimal.ZERO);
        var free = lesson(student, 60, ScheduledLesson.Status.COMPLETED);
        var pending = lesson(student, 60, ScheduledLesson.Status.SCHEDULED);
        when(schedules.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(1L, from, to)).thenReturn(List.of(unpriced, paid, paidAgain, free, pending));
        when(records.findByStudentTeacherIdAndLessonDateBetween(1L, from, to)).thenReturn(List.of());
        var income = service.report(from, to).income();
        assertEquals(new BigDecimal("300.30"), income.total());
        assertEquals(3, income.pricedLessons());
        assertEquals(1, income.unpricedLessons());
        assertEquals(new BigDecimal("100.10"), income.averagePerLesson());
        assertEquals(new BigDecimal("100.10"), income.averagePerHour());
        assertEquals(new BigDecimal("300.30"), income.students().getFirst().amount());
        assertEquals(new BigDecimal("300.30"), service.report(from, to).days().getFirst().income());
    }

    @Test void emptyReportHasUnknownAttendanceAndCompleteCalendar() {
        TeacherContext.set(2L);
        when(schedules.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(2L, from, to)).thenReturn(List.of());
        when(records.findByStudentTeacherIdAndLessonDateBetween(2L, from, to)).thenReturn(List.of());
        var report = service.report(from, to);
        assertNull(report.summary().attendanceRate());
        assertEquals(0, report.summary().taughtMinutes());
        assertEquals(30, report.days().size());
        verify(records).findByStudentTeacherIdAndLessonDateBetween(2L, from, to);
        verify(schedules).findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(2L, from, to);
    }

    @Test void excludesCancelledAndDoesNotDoubleCountClassroomRecords() {
        Student student = mock(Student.class);
        when(student.getTeacherId()).thenReturn(1L);
        when(student.getId()).thenReturn(7L);
        when(student.getSubject()).thenReturn("英语");
        var done = lesson(student, 90, ScheduledLesson.Status.COMPLETED);
        var leave = lesson(student, 60, ScheduledLesson.Status.LEAVE);
        var pending = lesson(student, 60, ScheduledLesson.Status.SCHEDULED);
        var cancelled = lesson(student, 120, ScheduledLesson.Status.CANCELLED);
        when(schedules.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(1L, from, to)).thenReturn(List.of(done, leave, pending, cancelled));
        when(records.findByStudentTeacherIdAndLessonDateBetween(1L, from, to)).thenReturn(List.of(new LessonRecord(student, "课堂记录", 90, "内容", "", "", from)));
        var report = service.report(from, to);
        var summary = report.summary();
        assertEquals(3, summary.planned());
        assertEquals(1, summary.cancelled());
        assertEquals(1, summary.completed());
        assertEquals(90, summary.taughtMinutes());
        assertEquals(210, summary.plannedMinutes());
        assertEquals(90, summary.recordedMinutes());
        assertEquals(50.0, summary.attendanceRate());
        assertEquals(1, summary.taughtStudents());
        assertEquals(1, summary.teachingDays());
        assertEquals(90, report.subjects().getFirst().minutes());
        assertEquals(1, report.days().getFirst().records());
    }

    @Test void rejectsReversedOrOversizedRangesBeforeQuerying() {
        assertThrows(ResponseStatusException.class, () -> service.report(to, from));
        assertThrows(ResponseStatusException.class, () -> service.report(from, from.plusDays(366)));
        verifyNoInteractions(schedules, records);
    }

    private ScheduledLesson lesson(Student student, int minutes, ScheduledLesson.Status status) {
        var lesson = new ScheduledLesson(student, from, LocalTime.of(9, 0), minutes, "阅读");
        lesson.setStatus(status);
        return lesson;
    }

    @Test void attendanceImmediatelyCountsPlannedMinutesAndUndoReversesIt() {
        TeacherContext.set(1L);
        Student student = mock(Student.class);
        when(student.getTeacherId()).thenReturn(1L);
        when(student.getId()).thenReturn(7L);
        when(student.getSubject()).thenReturn("英语");
        var lesson = new ScheduledLesson(student, to, LocalTime.of(17, 0), 90, "阅读");
        when(schedules.findByIdAndTeacherId(9L, 1L)).thenReturn(java.util.Optional.of(lesson));
        when(schedules.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(1L, from, to)).thenReturn(List.of(lesson));
        when(records.findByStudentTeacherIdAndLessonDateBetween(1L, from, to)).thenReturn(List.of());
        var courseService = new ScheduleService(schedules, mock(com.teacherfeedback.api.dao.StudentRepository.class));
        assertEquals(0, service.report(from, to).summary().taughtMinutes());
        courseService.status(9L, ScheduledLesson.Status.COMPLETED);
        assertEquals(90, service.report(from, to).summary().taughtMinutes());
        assertEquals(100.0, service.report(from, to).summary().attendanceRate());
        courseService.status(9L, ScheduledLesson.Status.SCHEDULED);
        assertEquals(0, service.report(from, to).summary().taughtMinutes());
        assertNull(service.report(from, to).summary().attendanceRate());
    }
}

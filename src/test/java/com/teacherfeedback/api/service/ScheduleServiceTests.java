package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.ScheduledLessonRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.ScheduleDtos.SaveRequest;
import com.teacherfeedback.api.model.ScheduledLesson;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTests {
    @Mock ScheduledLessonRepository lessons;
    @Mock StudentRepository students;

    @AfterEach
    void clearTeacher() {
        TeacherContext.clear();
    }

    @Test
    void overdueCoursesAreScopedToCurrentTeacherAndBeforeToday() {
        TeacherContext.set(7L);
        LocalDate today = LocalDate.now();
        when(lessons.findByTeacherIdAndStatusAndLessonDateBeforeOrderByLessonDateAscStartTimeAsc(7L, ScheduledLesson.Status.SCHEDULED, today)).thenReturn(List.of());
        assertEquals(0, service().overdue().size());
        verify(lessons).findByTeacherIdAndStatusAndLessonDateBeforeOrderByLessonDateAscStartTimeAsc(7L, ScheduledLesson.Status.SCHEDULED, today);
    }

    @Test
    void createsBoundedRecurringCoursesFromOriginalDate() {
        Student student = new Student(1L, "小雨", "初二", "英语", "");
        when(students.findById(3L)).thenReturn(Optional.of(student));
        var saved = new java.util.ArrayList<ScheduledLesson>();
        when(lessons.save(org.mockito.ArgumentMatchers.any(ScheduledLesson.class))).thenAnswer(call -> {
            ScheduledLesson lesson = call.getArgument(0); saved.add(lesson); return lesson;
        });
        LocalDate date = LocalDate.of(2028, 1, 31);
        for (String mode : List.of("WEEKLY", "BIWEEKLY", "MONTHLY")) {
            saved.clear();
            service().create(new SaveRequest(3L, date, LocalTime.of(10, 0), 60, "英语", mode, 3));
            assertEquals(3, saved.size());
            assertEquals(mode.equals("MONTHLY") ? date.plusMonths(2) : date.plusWeeks(mode.equals("WEEKLY") ? 2 : 4), saved.get(2).getLessonDate());
        }
    }

    @Test
    void defaultsOptionalCourseTitleToSubject() {
        Student student = new Student("小雨", "初二", "英语", "");
        assertEquals("英语课堂", new ScheduledLesson(student, LocalDate.now(), LocalTime.NOON, 60, null).getCourseTitle());
        assertEquals("英语课堂", new ScheduledLesson(student, LocalDate.now(), LocalTime.NOON, 60, "  ").getCourseTitle());
    }

    @Test
    void recurringConflictDoesNotSaveAnyCourses() {
        Student student = new Student(1L, "小雨", "初二", "英语", "");
        when(students.findById(3L)).thenReturn(Optional.of(student));
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(lessons.findByTeacherIdAndLessonDateOrderByStartTimeAsc(1L, date)).thenReturn(List.of());
        when(lessons.findByTeacherIdAndLessonDateOrderByStartTimeAsc(1L, date.plusWeeks(1)))
            .thenReturn(List.of(new ScheduledLesson(student, date.plusWeeks(1), LocalTime.of(10, 0), 60, "英语")));
        assertThrows(ResponseStatusException.class, () -> service().create(new SaveRequest(3L, date, LocalTime.of(10, 0), 60, "英语", "WEEKLY", 4)));
        org.mockito.Mockito.verify(lessons, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listsOnlyCurrentTeachersWeek() {
        TeacherContext.set(2L);
        LocalDate monday = LocalDate.of(2026, 9, 21);
        LocalDate sunday = monday.plusDays(6);
        when(lessons.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(2L, monday, sunday))
            .thenReturn(List.of());

        assertEquals(0, service().list(monday, sunday).size());
        verify(lessons).findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(2L, monday, sunday);
    }

    @Test
    void rejectsOverlappingCourse() {
        TeacherContext.set(1L);
        LocalDate date = LocalDate.of(2026, 9, 23);
        Student student = new Student(1L, "小雨", "初二", "英语", "");
        ScheduledLesson existing = new ScheduledLesson(student, date, LocalTime.of(10, 0), 60, "英语");
        when(students.findById(3L)).thenReturn(Optional.of(student));
        when(lessons.findByTeacherIdAndLessonDateOrderByStartTimeAsc(1L, date)).thenReturn(List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service().create(
            new SaveRequest(3L, date, LocalTime.of(10, 30), 45, "阅读")
        ));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void rejectsAnotherTeachersStudent() {
        TeacherContext.set(2L);
        Student student = new Student(1L, "小雨", "初二", "英语", "");
        when(students.findById(3L)).thenReturn(Optional.of(student));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service().create(
            new SaveRequest(3L, LocalDate.of(2026, 9, 23), LocalTime.of(9, 0), 60, "阅读")
        ));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void rejectsCourseThatCrossesMidnight() {
        TeacherContext.set(1L);
        Student student = new Student(1L, "小雨", "初二", "英语", "");
        when(students.findById(3L)).thenReturn(Optional.of(student));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service().create(
            new SaveRequest(3L, LocalDate.of(2026, 9, 23), LocalTime.of(23, 30), 60, "阅读")
        ));
        assertEquals(400, error.getStatusCode().value());
    }

    private ScheduleService service() {
        return new ScheduleService(lessons, students);
    }

    @Test
    void completedLessonCanBeRestoredWithoutRemovingItsSchedule() {
        TeacherContext.set(1L);
        var date = LocalDate.of(2026, 11, 4);
        var student = new Student(1L, "小雨", "初二", "英语", "");
        var lesson = new ScheduledLesson(student, date, LocalTime.of(9, 0), 60, "阅读");
        lesson.setStatus(ScheduledLesson.Status.COMPLETED);
        when(lessons.findByIdAndTeacherId(8L, 1L)).thenReturn(Optional.of(lesson));
        when(lessons.findByTeacherIdAndLessonDateOrderByStartTimeAsc(1L, date)).thenReturn(List.of());
        assertEquals("SCHEDULED", service().status(8L, ScheduledLesson.Status.SCHEDULED).status());
        assertEquals(60, lesson.getDurationMinutes());
    }

    @Test
    void restorationChecksWhetherOriginalTimeWasTaken() {
        TeacherContext.set(1L);
        var date = LocalDate.of(2026, 11, 4);
        var student = new Student(1L, "小雨", "初二", "英语", "");
        var lesson = new ScheduledLesson(student, date, LocalTime.of(9, 0), 60, "阅读");
        lesson.setStatus(ScheduledLesson.Status.CANCELLED);
        var conflict = new ScheduledLesson(student, date, LocalTime.of(9, 30), 60, "语法");
        when(lessons.findByIdAndTeacherId(8L, 1L)).thenReturn(Optional.of(lesson));
        when(lessons.findByTeacherIdAndLessonDateOrderByStartTimeAsc(1L, date)).thenReturn(List.of(conflict));
        var error = assertThrows(ResponseStatusException.class, () -> service().status(8L, ScheduledLesson.Status.SCHEDULED));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(ScheduledLesson.Status.CANCELLED, lesson.getStatus());
    }

    @Test
    void nonScheduledStatusMustBeUndoneBeforeAnotherConfirmation() {
        TeacherContext.set(1L);
        var student = new Student(1L, "小雨", "初二", "英语", "");
        var lesson = new ScheduledLesson(student, LocalDate.of(2026, 11, 4), LocalTime.of(9, 0), 60, "阅读");
        lesson.setStatus(ScheduledLesson.Status.LEAVE);
        when(lessons.findByIdAndTeacherId(8L, 1L)).thenReturn(Optional.of(lesson));
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service().status(8L, ScheduledLesson.Status.COMPLETED)).getStatusCode().value());
        assertEquals("LEAVE", service().status(8L, ScheduledLesson.Status.LEAVE).status());
    }

    @Test
    void cannotUndoAnotherTeachersCourse() {
        TeacherContext.set(2L);
        when(lessons.findByIdAndTeacherId(8L, 2L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> service().status(8L, ScheduledLesson.Status.SCHEDULED)).getStatusCode().value());
    }
}

package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.ScheduledLessonRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.ScheduleDtos.Response;
import com.teacherfeedback.api.dto.ScheduleDtos.SaveRequest;
import com.teacherfeedback.api.model.ScheduledLesson;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ScheduleService {
    private final ScheduledLessonRepository lessons;
    private final StudentRepository students;

    public ScheduleService(ScheduledLessonRepository lessons, StudentRepository students) {
        this.lessons = lessons;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<Response> list(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 31) {
            throw new ResponseStatusException(BAD_REQUEST, "日期范围不正确");
        }
        return lessons.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(teacherId(), from, to)
            .stream().map(this::response).toList();
    }

    @Transactional
    public Response create(SaveRequest request) {
        Student student = TeacherContext.requireStudent(students, request.studentId());
        String mode = request.repeatMode() == null ? "ONCE" : request.repeatMode();
        if (!List.of("ONCE", "WEEKLY", "BIWEEKLY", "MONTHLY").contains(mode)) {
            throw new ResponseStatusException(BAD_REQUEST, "重复模式不正确");
        }
        int count = mode.equals("ONCE") ? 1 : request.repeatCount() == null ? 0 : request.repeatCount();
        if (count < 1 || count > 52) throw new ResponseStatusException(BAD_REQUEST, "请选择排课次数（1至52次）");
        var dates = new java.util.ArrayList<LocalDate>();
        for (int index = 0; index < count; index++) {
            LocalDate date = switch (mode) {
                case "WEEKLY" -> request.lessonDate().plusWeeks(index);
                case "BIWEEKLY" -> request.lessonDate().plusWeeks(index * 2L);
                case "MONTHLY" -> request.lessonDate().plusMonths(index);
                default -> request.lessonDate();
            };
            validateTime(new SaveRequest(request.studentId(), date, request.startTime(), request.durationMinutes(), request.courseTitle()), null);
            dates.add(date);
        }
        Response first = null;
        for (LocalDate date : dates) {
            Response saved = response(lessons.save(new ScheduledLesson(student, date, request.startTime(), request.durationMinutes(), request.courseTitle())));
            if (first == null) first = saved;
        }
        return first;
    }

    @Transactional(readOnly = true)
    public List<Response> overdue() {
        return lessons.findByTeacherIdAndStatusAndLessonDateBeforeOrderByLessonDateAscStartTimeAsc(
            teacherId(), ScheduledLesson.Status.SCHEDULED, LocalDate.now()).stream().map(this::response).toList();
    }

    @Transactional
    public Response update(Long id, SaveRequest request) {
        ScheduledLesson lesson = requireLesson(id);
        if (lesson.getStatus() == ScheduledLesson.Status.COMPLETED) {
            throw new ResponseStatusException(CONFLICT, "请先撤销签到再修改课程");
        }
        Student student = TeacherContext.requireStudent(students, request.studentId());
        validateTime(request, id);
        lesson.update(student, request.lessonDate(), request.startTime(), request.durationMinutes(), request.courseTitle());
        return response(lesson);
    }

    @Transactional
    public Response status(Long id, ScheduledLesson.Status status) {
        ScheduledLesson lesson = requireLesson(id);
        if (lesson.getStatus() == status) return response(lesson);
        if (lesson.getStatus() != ScheduledLesson.Status.SCHEDULED && status != ScheduledLesson.Status.SCHEDULED) {
            throw new ResponseStatusException(CONFLICT, "请先撤销当前课程状态");
        }
        if (status == ScheduledLesson.Status.SCHEDULED) {
            validateTime(new SaveRequest(lesson.getStudent().getId(), lesson.getLessonDate(),
                lesson.getStartTime(), lesson.getDurationMinutes(), lesson.getCourseTitle()), id);
        }
        lesson.setStatus(status);
        return response(lesson);
    }

    private void validateTime(SaveRequest request, Long editingId) {
        LocalTime end = request.startTime().plusMinutes(request.durationMinutes());
        if (end.isBefore(request.startTime()) || end.equals(request.startTime())) {
            throw new ResponseStatusException(BAD_REQUEST, "课程不能跨越午夜");
        }
        for (ScheduledLesson existing : lessons.findByTeacherIdAndLessonDateOrderByStartTimeAsc(
            teacherId(), request.lessonDate()
        )) {
            if ((editingId != null && editingId.equals(existing.getId()))
                || existing.getStatus() != ScheduledLesson.Status.SCHEDULED) continue;
            LocalTime existingEnd = existing.getStartTime().plusMinutes(existing.getDurationMinutes());
            if (request.startTime().isBefore(existingEnd) && existing.getStartTime().isBefore(end)) {
                throw new ResponseStatusException(CONFLICT, "此时间已有课程");
            }
        }
    }

    private ScheduledLesson requireLesson(Long id) {
        return lessons.findByIdAndTeacherId(id, teacherId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "课程不存在"));
    }

    private Long teacherId() {
        return TeacherContext.currentTeacherId() == null ? 1L : TeacherContext.currentTeacherId();
    }

    private Response response(ScheduledLesson lesson) {
        Student student = lesson.getStudent();
        return new Response(
            lesson.getId(), student.getId(), student.getName(), student.getSubject(),
            lesson.getLessonDate(), lesson.getStartTime(), lesson.getDurationMinutes(),
            lesson.getCourseTitle(), lesson.getStatus().name(), lesson.getAttendanceIncome()
        );
    }
}

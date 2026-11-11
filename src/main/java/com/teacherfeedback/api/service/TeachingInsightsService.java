package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.ScheduledLessonRepository;
import com.teacherfeedback.api.model.ScheduledLesson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class TeachingInsightsService {
    public record Summary(int planned, int completed, int pending, int leave, int cancelled,
                          int plannedMinutes, int taughtMinutes, int teachingDays,
                          int taughtStudents, int records, int recordedMinutes, Double attendanceRate) {}
    public record Day(LocalDate date, int planned, int completed, int taughtMinutes, int records, BigDecimal income) {}
    public record Subject(String name, int completed, int minutes) {}
    public record StudentIncome(Long studentId, String name, int lessons, BigDecimal amount) {}
    public record Income(BigDecimal total, int pricedLessons, int unpricedLessons,
                         BigDecimal averagePerLesson, BigDecimal averagePerHour, List<StudentIncome> students) {}
    public record Report(LocalDate from, LocalDate to, Summary summary, List<Day> days, List<Subject> subjects, Income income) {}

    private final ScheduledLessonRepository schedules;
    private final LessonRecordRepository records;

    public TeachingInsightsService(ScheduledLessonRepository schedules, LessonRecordRepository records) {
        this.schedules = schedules;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new ResponseStatusException(BAD_REQUEST, "统计日期范围不能超过一年");
        }
        Long teacherId = TeacherContext.currentTeacherId() == null ? 1L : TeacherContext.currentTeacherId();
        var lessons = schedules.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(teacherId, from, to);
        var lessonRecords = records.findByStudentTeacherIdAndLessonDateBetween(teacherId, from, to);
        Map<LocalDate, int[]> daily = new LinkedHashMap<>();
        from.datesUntil(to.plusDays(1)).forEach(date -> daily.put(date, new int[4]));
        Map<String, int[]> subjects = new TreeMap<>();
        Set<Long> students = new HashSet<>();
        Set<LocalDate> teachingDays = new HashSet<>();
        Map<LocalDate, BigDecimal> dailyIncome = new HashMap<>();
        Map<Long, StudentIncome> studentIncome = new HashMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO.setScale(2);
        int pricedLessons = 0, unpricedLessons = 0, pricedMinutes = 0;
        int planned = 0, completed = 0, pending = 0, leave = 0, cancelled = 0, plannedMinutes = 0, taughtMinutes = 0;
        for (var lesson : lessons) {
            if (lesson.getStatus() == ScheduledLesson.Status.CANCELLED) { cancelled++; continue; }
            planned++;
            plannedMinutes += lesson.getDurationMinutes();
            int[] day = daily.get(lesson.getLessonDate());
            day[0]++;
            switch (lesson.getStatus()) {
                case COMPLETED -> {
                    BigDecimal amount = lesson.getAttendanceIncome();
                    if (amount == null) {
                        unpricedLessons++;
                    } else {
                        pricedLessons++;
                        pricedMinutes += lesson.getDurationMinutes();
                        totalIncome = totalIncome.add(amount);
                        dailyIncome.merge(lesson.getLessonDate(), amount, BigDecimal::add);
                        Long studentId = lesson.getStudent().getId();
                        StudentIncome previous = studentIncome.get(studentId);
                        studentIncome.put(studentId, new StudentIncome(studentId, lesson.getStudent().getName(),
                            previous == null ? 1 : previous.lessons() + 1,
                            previous == null ? amount : previous.amount().add(amount)));
                    }
                    completed++;
                    taughtMinutes += lesson.getDurationMinutes();
                    day[1]++;
                    day[2] += lesson.getDurationMinutes();
                    teachingDays.add(lesson.getLessonDate());
                    students.add(lesson.getStudent().getId());
                    String subject = lesson.getStudent().getSubject();
                    int[] totals = subjects.computeIfAbsent(subject == null || subject.isBlank() ? "未分类" : subject, key -> new int[2]);
                    totals[0]++;
                    totals[1] += lesson.getDurationMinutes();
                }
                case LEAVE -> leave++;
                case SCHEDULED -> pending++;
                default -> { }
            }
        }
        int recordedMinutes = 0;
        for (var record : lessonRecords) {
            daily.get(record.getLessonDate())[3]++;
            recordedMinutes += record.getDurationMinutes();
        }
        Double attendance = completed + leave == 0 ? null : Math.round(completed * 1000.0 / (completed + leave)) / 10.0;
        return new Report(from, to,
            new Summary(planned, completed, pending, leave, cancelled, plannedMinutes, taughtMinutes,
                teachingDays.size(), students.size(), lessonRecords.size(), recordedMinutes, attendance),
            daily.entrySet().stream().map(entry -> new Day(entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2], entry.getValue()[3], dailyIncome.getOrDefault(entry.getKey(), BigDecimal.ZERO.setScale(2)))).toList(),
            subjects.entrySet().stream().map(entry -> new Subject(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList(),
            new Income(totalIncome, pricedLessons, unpricedLessons,
                pricedLessons == 0 ? null : totalIncome.divide(BigDecimal.valueOf(pricedLessons), 2, RoundingMode.HALF_UP),
                pricedMinutes == 0 ? null : totalIncome.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(pricedMinutes), 2, RoundingMode.HALF_UP),
                studentIncome.values().stream().sorted(Comparator.comparing(StudentIncome::amount).reversed()
                    .thenComparing(StudentIncome::studentId)).toList()));
    }
}

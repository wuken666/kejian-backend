package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "scheduled_lessons")
public class ScheduledLesson {
    // COMPLETED retains the legacy stored value; it now represents confirmed attendance.
    public enum Status { SCHEDULED, COMPLETED, LEAVE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "attendance_income", precision = 8, scale = 2)
    private BigDecimal attendanceIncome;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ScheduledLesson() {
    }

    public ScheduledLesson(Student student, LocalDate date, LocalTime time, int duration, String title) {
        this.teacherId = student.getTeacherId();
        this.student = student;
        this.status = Status.SCHEDULED;
        this.createdAt = LocalDateTime.now();
        update(student, date, time, duration, title);
    }

    public void update(Student student, LocalDate date, LocalTime time, int duration, String title) {
        this.student = student;
        this.lessonDate = date;
        this.startTime = time;
        this.durationMinutes = duration;
        this.courseTitle = title == null || title.isBlank() ? student.getSubject() + "课堂" : title.strip();
        this.status = Status.SCHEDULED;
        this.updatedAt = LocalDateTime.now();
        this.attendanceIncome = null;
    }

    public void setStatus(Status status) {
        if (this.status == status) return;
        // Freeze the fee at attendance confirmation; later price changes must not rewrite history.
        this.attendanceIncome = status == Status.COMPLETED ? student.getLessonFee() : null;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public Student getStudent() { return student; }
    public LocalDate getLessonDate() { return lessonDate; }
    public LocalTime getStartTime() { return startTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getCourseTitle() { return courseTitle; }
    public Status getStatus() { return status; }
    public BigDecimal getAttendanceIncome() { return attendanceIncome; }
}

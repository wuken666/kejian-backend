package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_records")
public class LessonRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheduled_lesson_id")
    private Long scheduledLessonId;

    public void linkScheduledLesson(Long id) { this.scheduledLessonId = id; }
    public Long getScheduledLessonId() { return scheduledLessonId; }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String performance;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String homework;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected LessonRecord() {
    }

    public LessonRecord(Student student, String courseTitle, Integer durationMinutes, String content,
                        String performance, String homework, LocalDate lessonDate) {
        this.student = student;
        this.courseTitle = courseTitle;
        this.durationMinutes = durationMinutes;
        this.content = content;
        this.performance = performance;
        this.homework = homework;
        this.lessonDate = lessonDate;
        this.createdAt = LocalDateTime.now();
    }

    public void update(
        String courseTitle,
        Integer durationMinutes,
        String content,
        String performance,
        String homework,
        LocalDate lessonDate
    ) {
        this.courseTitle = courseTitle.strip();
        this.durationMinutes = durationMinutes;
        this.content = content.strip();
        this.performance = performance == null ? "" : performance.strip();
        this.homework = homework == null ? "" : homework.strip();
        this.lessonDate = lessonDate;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getCourseTitle() { return courseTitle; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getContent() { return content; }
    public String getPerformance() { return performance; }
    public String getHomework() { return homework; }
    public LocalDate getLessonDate() { return lessonDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

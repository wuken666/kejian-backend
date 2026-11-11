package com.teacherfeedback.api.model;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.SaveLessonDraftRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_drafts")
public class LessonDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "lesson_date")
    private LocalDate lessonDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String performance;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String homework;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LessonDraft() {
    }

    public LessonDraft(Student student, SaveLessonDraftRequest request) {
        this.student = student;
        update(request);
    }

    public void update(SaveLessonDraftRequest request) {
        this.courseTitle = safe(request.courseTitle());
        this.durationMinutes = request.durationMinutes();
        this.lessonDate = request.lessonDate();
        this.content = safe(request.content());
        this.performance = safe(request.performance());
        this.homework = safe(request.homework());
        this.analysisId = request.analysisId();
        this.updatedAt = LocalDateTime.now();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    public Student getStudent() { return student; }
    public String getCourseTitle() { return courseTitle; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public LocalDate getLessonDate() { return lessonDate; }
    public String getContent() { return content; }
    public String getPerformance() { return performance; }
    public String getHomework() { return homework; }
    public Long getAnalysisId() { return analysisId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

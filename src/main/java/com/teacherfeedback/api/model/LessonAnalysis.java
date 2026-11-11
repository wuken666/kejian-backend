package com.teacherfeedback.api.model;

import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "lesson_analyses")
public class LessonAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "ocr_text", nullable = false, columnDefinition = "LONGTEXT")
    private String ocrText;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String performance;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String homework;

    @Column(name = "feedback_draft", nullable = false, columnDefinition = "LONGTEXT")
    private String feedbackDraft;

    @Column(name = "feedback_tone", nullable = false, length = 20)
    private String feedbackTone;

    @Column(name = "feedback_length", nullable = false, length = 20)
    private String feedbackLength;

    @Column(name = "missing_fields", nullable = false, length = 255)
    private String missingFields;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "student_matched", nullable = false)
    private boolean studentMatched;

    @Column(name = "detected_student_names", nullable = false, length = 255)
    private String detectedStudentNames;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected LessonAnalysis() {
    }

    public LessonAnalysis(
        Student student,
        String ocrText,
        StructuredLesson structuredLesson,
        FeedbackPreferences preferences
    ) {
        this.student = student;
        this.ocrText = ocrText;
        this.courseTitle = structuredLesson.courseTitle();
        this.durationMinutes = structuredLesson.durationMinutes();
        this.content = structuredLesson.content();
        this.performance = structuredLesson.performance();
        this.homework = structuredLesson.homework();
        this.feedbackDraft = structuredLesson.feedbackDraft();
        this.feedbackTone = preferences.tone();
        this.feedbackLength = preferences.length();
        this.missingFields = String.join(",", structuredLesson.missingFields());
        this.confidence = structuredLesson.confidence();
        this.studentMatched = structuredLesson.studentMatched();
        this.detectedStudentNames = String.join(",", structuredLesson.detectedStudentNames());
        this.warnings = String.join("\n", structuredLesson.warnings());
        this.createdAt = LocalDateTime.now();
    }

    public void confirm() {
        confirmedAt = LocalDateTime.now();
    }

    public boolean matches(CreateLessonRequest request) {
        FeedbackPreferences preferences = FeedbackPreferences.fromNullable(
            request.feedbackTone(),
            request.feedbackLength()
        );
        return same(courseTitle, request.courseTitle())
            && same(content, request.content())
            && same(performance, request.performance())
            && same(homework, request.homework())
            && same(feedbackTone, preferences.tone())
            && same(feedbackLength, preferences.length())
            && (durationMinutes == null || durationMinutes.equals(request.durationMinutes()));
    }

    private boolean same(String left, String right) {
        return left.strip().equals(right.strip());
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getOcrText() { return ocrText; }
    public String getCourseTitle() { return courseTitle; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getContent() { return content; }
    public String getPerformance() { return performance; }
    public String getHomework() { return homework; }
    public String getFeedbackDraft() { return feedbackDraft; }
    public String getFeedbackTone() { return feedbackTone; }
    public String getFeedbackLength() { return feedbackLength; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isStudentMatched() { return studentMatched; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }

    public List<String> getMissingFields() {
        if (missingFields.isBlank()) return List.of();
        return List.of(missingFields.split(","));
    }

    public List<String> getDetectedStudentNames() {
        if (detectedStudentNames.isBlank()) return List.of();
        return List.of(detectedStudentNames.split(","));
    }

    public List<String> getWarnings() {
        if (warnings.isBlank()) return List.of();
        return List.of(warnings.split("\n"));
    }
}

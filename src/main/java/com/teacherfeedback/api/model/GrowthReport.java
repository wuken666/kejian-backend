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
@Table(name = "growth_reports")
public class GrowthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "lesson_count", nullable = false)
    private Integer lessonCount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected GrowthReport() {
    }

    public GrowthReport(
        Student student,
        Integer lessonCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        String content
    ) {
        this.student = student;
        this.lessonCount = lessonCount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.content = content.strip();
        this.createdAt = LocalDateTime.now();
    }

    public void regenerate(
        Integer lessonCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        String content
    ) {
        this.lessonCount = lessonCount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.content = content.strip();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Integer getLessonCount() { return lessonCount; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

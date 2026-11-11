package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_record_id", nullable = false)
    private LessonRecord lessonRecord;

    @Column(nullable = false, length = 20)
    private String audience;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_source", nullable = false, length = 20)
    private FeedbackGenerationSource generationSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "revised_at")
    private LocalDateTime revisedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected Feedback() {
    }

    public Feedback(LessonRecord lessonRecord, String audience, String content) {
        this(lessonRecord, audience, content, FeedbackGenerationSource.AI);
    }

    public Feedback(
        LessonRecord lessonRecord,
        String audience,
        String content,
        FeedbackGenerationSource generationSource
    ) {
        this.lessonRecord = lessonRecord;
        this.audience = audience;
        this.content = content;
        this.generationSource = generationSource;
        this.createdAt = LocalDateTime.now();
    }

    public void revise(String revisedContent) {
        this.content = revisedContent.strip();
        this.generationSource = FeedbackGenerationSource.MANUAL;
        this.revisedAt = LocalDateTime.now();
        this.confirmedAt = this.revisedAt;
    }

    public void replaceGenerated(
        String generatedContent,
        FeedbackGenerationSource generationSource
    ) {
        this.content = generatedContent.strip();
        this.generationSource = generationSource;
        this.revisedAt = LocalDateTime.now();
        this.confirmedAt = null;
    }

    // 老师明确核对反馈后记录确认时间，供其他设备和首页任务状态共同使用。
    public void confirm() {
        this.confirmedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LessonRecord getLessonRecord() { return lessonRecord; }
    public String getContent() { return content; }
    public FeedbackGenerationSource getGenerationSource() { return generationSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRevisedAt() { return revisedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}

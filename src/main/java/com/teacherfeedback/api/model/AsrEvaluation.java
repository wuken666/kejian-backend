package com.teacherfeedback.api.model;

import com.teacherfeedback.api.service.CharacterErrorRate;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "asr_evaluations")
public class AsrEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Column(name = "reference_text", nullable = false, columnDefinition = "TEXT")
    private String referenceText;

    @Column(name = "recognized_text", nullable = false, columnDefinition = "TEXT")
    private String recognizedText;

    @Column(name = "reference_characters", nullable = false)
    private int referenceCharacters;

    @Column(name = "recognized_characters", nullable = false)
    private int recognizedCharacters;

    @Column(nullable = false)
    private int substitutions;

    @Column(nullable = false)
    private int deletions;

    @Column(nullable = false)
    private int insertions;

    @Column(name = "character_error_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal characterErrorRate;

    @Column(name = "inference_seconds", nullable = false, precision = 10, scale = 3)
    private BigDecimal inferenceSeconds;

    @Column(name = "hotwords_text", nullable = false, columnDefinition = "TEXT")
    private String hotwordsText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AsrEvaluation() {
    }

    public AsrEvaluation(
        Student student,
        String originalFilename,
        String courseTitle,
        String referenceText,
        String recognizedText,
        CharacterErrorRate.Result metrics,
        double inferenceSeconds,
        List<String> hotwords
    ) {
        this.student = student;
        this.originalFilename = originalFilename;
        this.courseTitle = courseTitle;
        this.referenceText = referenceText;
        this.recognizedText = recognizedText;
        this.referenceCharacters = metrics.referenceCharacters();
        this.recognizedCharacters = metrics.recognizedCharacters();
        this.substitutions = metrics.substitutions();
        this.deletions = metrics.deletions();
        this.insertions = metrics.insertions();
        this.characterErrorRate = BigDecimal.valueOf(metrics.rate()).setScale(6, RoundingMode.HALF_UP);
        this.inferenceSeconds = BigDecimal.valueOf(inferenceSeconds).setScale(3, RoundingMode.HALF_UP);
        this.hotwordsText = String.join("\n", hotwords);
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getOriginalFilename() { return originalFilename; }
    public String getCourseTitle() { return courseTitle; }
    public String getReferenceText() { return referenceText; }
    public String getRecognizedText() { return recognizedText; }
    public int getReferenceCharacters() { return referenceCharacters; }
    public int getRecognizedCharacters() { return recognizedCharacters; }
    public int getSubstitutions() { return substitutions; }
    public int getDeletions() { return deletions; }
    public int getInsertions() { return insertions; }
    public double getCharacterErrorRate() { return characterErrorRate.doubleValue(); }
    public double getInferenceSeconds() { return inferenceSeconds.doubleValue(); }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<String> getHotwords() {
        return hotwordsText.isBlank() ? List.of() : List.of(hotwordsText.split("\\n"));
    }
}

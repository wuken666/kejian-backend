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

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "lesson_session_segments")
public class LessonSessionSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_session_id", nullable = false)
    private LessonSession lessonSession;

    @Column(name = "client_segment_id", nullable = false, length = 80)
    private String clientSegmentId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "asr_status", nullable = false, length = 20)
    private String asrStatus;

    @Column(name = "audio_quality", nullable = false, length = 20)
    private String audioQuality;

    @Column(name = "audio_rms")
    private BigDecimal audioRms;

    @Column(name = "clipping_ratio")
    private BigDecimal clippingRatio;

    @Column(name = "asr_inference_seconds")
    private BigDecimal asrInferenceSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected LessonSessionSegment() {
    }

    public LessonSessionSegment(
        LessonSession lessonSession,
        String clientSegmentId,
        int sequenceNumber,
        int durationSeconds,
        String transcript
    ) {
        this.lessonSession = lessonSession;
        this.clientSegmentId = clientSegmentId.strip();
        this.sequenceNumber = sequenceNumber;
        this.durationSeconds = durationSeconds;
        this.transcript = transcript == null ? "" : transcript.strip();
        this.asrStatus = this.transcript.isBlank() ? "UNKNOWN" : "TRANSCRIBED";
        this.audioQuality = "UNKNOWN";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LessonSession getLessonSession() { return lessonSession; }
    public String getClientSegmentId() { return clientSegmentId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getTranscript() { return transcript; }
    public String getAsrStatus() { return asrStatus; }
    public String getAudioQuality() { return audioQuality; }
    public Double getAudioRms() { return audioRms == null ? null : audioRms.doubleValue(); }
    public Double getClippingRatio() { return clippingRatio == null ? null : clippingRatio.doubleValue(); }
    public Double getAsrInferenceSeconds() {
        return asrInferenceSeconds == null ? null : asrInferenceSeconds.doubleValue();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ASR 失败时先保留空转写，待后续重试识别后再更新文本。
    public void updateTranscript(String transcript) {
        this.transcript = transcript == null ? "" : transcript.strip();
    }

    // 保存识别结果和音频质量摘要，不把课堂音频内容复制到诊断字段。
    public void completeTranscription(
        String transcript,
        String audioQuality,
        double audioRms,
        double clippingRatio,
        double inferenceSeconds
    ) {
        updateTranscript(transcript);
        this.asrStatus = this.transcript.isBlank() ? "EMPTY" : "TRANSCRIBED";
        this.audioQuality = audioQuality == null || audioQuality.isBlank() ? "UNKNOWN" : audioQuality;
        this.audioRms = BigDecimal.valueOf(audioRms);
        this.clippingRatio = BigDecimal.valueOf(clippingRatio);
        this.asrInferenceSeconds = BigDecimal.valueOf(inferenceSeconds);
    }

    // ASR 调用失败时保留已落盘音频，并标记为可诊断、可重试的失败状态。
    public void markTranscriptionFailed() {
        this.asrStatus = "FAILED";
        this.audioQuality = "UNKNOWN";
    }
}

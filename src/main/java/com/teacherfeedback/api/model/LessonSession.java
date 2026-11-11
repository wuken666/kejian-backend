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

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_sessions")
public class LessonSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "course_title", nullable = false, length = 120)
    private String courseTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "resumed_at", nullable = false)
    private LocalDateTime resumedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "recording_owner_token", length = 80)
    private String recordingOwnerToken;

    @Column(name = "recording_owner_heartbeat_at")
    private LocalDateTime recordingOwnerHeartbeatAt;

    @Column(name = "client_captured_seconds")
    private Integer clientCapturedSeconds;

    protected LessonSession() {
    }

    public LessonSession(Student student, String courseTitle) {
        LocalDateTime now = LocalDateTime.now();
        this.student = student;
        this.courseTitle = courseTitle.strip();
        this.status = LessonSessionStatus.RECORDING;
        this.startedAt = now;
        this.resumedAt = now;
        this.updatedAt = now;
    }

    public void resume() {
        requireNotCompleted();
        LocalDateTime now = LocalDateTime.now();
        this.status = LessonSessionStatus.RECORDING;
        this.resumedAt = now;
        this.updatedAt = now;
    }

    public void pause() {
        requireNotCompleted();
        this.status = LessonSessionStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        requireNotCompleted();
        this.status = LessonSessionStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void touch() {
        requireNotCompleted();
        this.updatedAt = LocalDateTime.now();
    }

    // 录音页面领取本节课堂的写入权，避免多个页面同时上传同一节课。
    public void claimRecordingOwner(String ownerToken, LocalDateTime now) {
        requireNotCompleted();
        this.recordingOwnerToken = ownerToken;
        this.recordingOwnerHeartbeatAt = now;
        this.updatedAt = now;
    }

    // 录音分片上传成功或重新领取时刷新占用时间。
    public void heartbeatRecordingOwner(String ownerToken, LocalDateTime now) {
        requireNotCompleted();
        this.recordingOwnerToken = ownerToken;
        this.recordingOwnerHeartbeatAt = now;
        this.updatedAt = now;
    }

    // 判断当前页面的录音占用是否仍在有效期内。
    public boolean hasActiveRecordingOwner(LocalDateTime now, Duration lease) {
        return recordingOwnerToken != null
            && recordingOwnerHeartbeatAt != null
            && recordingOwnerHeartbeatAt.isAfter(now.minus(lease));
    }

    public boolean ownsRecording(String ownerToken) {
        return recordingOwnerToken != null && recordingOwnerToken.equals(ownerToken);
    }

    // 记录客户端实际采集时长，只允许递增，避免迟到的心跳覆盖更新的录音进度。
    public void reportClientCapturedSeconds(Integer capturedSeconds) {
        if (capturedSeconds == null) return;
        int normalizedSeconds = Math.max(0, capturedSeconds);
        if (clientCapturedSeconds == null || normalizedSeconds > clientCapturedSeconds) {
            clientCapturedSeconds = normalizedSeconds;
        }
    }

    private void requireNotCompleted() {
        if (status == LessonSessionStatus.COMPLETED) {
            throw new IllegalStateException("Completed lesson sessions cannot be changed");
        }
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getCourseTitle() { return courseTitle; }
    public LessonSessionStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getResumedAt() { return resumedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Integer getClientCapturedSeconds() { return clientCapturedSeconds; }
}

package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_error_events")
public class ClientErrorEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(nullable = false, length = 40)
    private String area;

    @Column(nullable = false, length = 80)
    private String context;

    @Column(name = "error_name", nullable = false, length = 100)
    private String errorName;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "user_agent", nullable = false, length = 500)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected ClientErrorEvent() {
    }

    public ClientErrorEvent(
        Long teacherId,
        String area,
        String context,
        String errorName,
        String message,
        String userAgent
    ) {
        this.teacherId = teacherId;
        this.area = normalize(area, "unknown");
        this.context = normalize(context, "unknown");
        this.errorName = normalize(errorName, "Error");
        this.message = normalize(message, "系统异常");
        this.userAgent = normalize(userAgent, "unknown");
        this.occurredAt = LocalDateTime.now();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replace('\r', ' ').replace('\n', ' ').strip();
    }
}

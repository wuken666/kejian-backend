package com.teacherfeedback.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminDashboardDtos {
    private AdminDashboardDtos() {
    }

    public record AdminOverview(
        long teacherCount,
        long sessionCount,
        long recordedSeconds,
        long incidentCount,
        double transcriptionCoverage
    ) {
    }

    public record AdminTeacherRow(
        long id,
        String name,
        long studentCount,
        long sessionCount,
        long recordedSeconds,
        long incidentCount,
        LocalDateTime lastActiveAt
    ) {
    }

    public record AdminSessionRow(
        long id,
        long teacherId,
        String teacherName,
        String studentName,
        String courseTitle,
        String status,
        LocalDateTime startedAt,
        LocalDateTime updatedAt,
        long recordedSeconds,
        Integer clientCapturedSeconds,
        long missingAudioSeconds,
        long segmentCount,
        Integer firstSequenceNumber,
        Integer lastSequenceNumber,
        long sequenceGapCount,
        LocalDateTime lastSegmentAt,
        long transcriptSegmentCount,
        long failedSegmentCount,
        long distortedSegmentCount,
        long silentSegmentCount,
        String health
    ) {
    }

    public record AdminIncidentRow(
        long id,
        Long teacherId,
        String teacherName,
        String area,
        String context,
        String errorName,
        String message,
        String userAgent,
        LocalDateTime occurredAt
    ) {
    }

    public record AdminDashboardResponse(
        int days,
        AdminOverview overview,
        List<AdminTeacherRow> teachers,
        List<AdminSessionRow> sessions,
        List<AdminIncidentRow> incidents,
        LocalDateTime generatedAt
    ) {
    }
}

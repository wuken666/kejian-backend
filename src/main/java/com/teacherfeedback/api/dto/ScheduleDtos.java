package com.teacherfeedback.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public final class ScheduleDtos {
    private ScheduleDtos() {
    }

    public record SaveRequest(
        @NotNull Long studentId,
        @NotNull LocalDate lessonDate,
        @NotNull LocalTime startTime,
        @NotNull @Min(15) @Max(360) Integer durationMinutes,
        @Size(max = 120) String courseTitle,
        @Pattern(regexp = "ONCE|WEEKLY|BIWEEKLY|MONTHLY") String repeatMode,
        @Min(1) @Max(52) Integer repeatCount
    ) {
        public SaveRequest(Long studentId, LocalDate lessonDate, LocalTime startTime, Integer durationMinutes, String courseTitle) {
            this(studentId, lessonDate, startTime, durationMinutes, courseTitle, null, null);
        }
    }

    public record StatusRequest(
        @NotBlank @Pattern(regexp = "SCHEDULED|COMPLETED|LEAVE|CANCELLED") String status
    ) {
    }

    public record Response(
        Long id,
        Long studentId,
        String studentName,
        String subject,
        LocalDate lessonDate,
        LocalTime startTime,
        int durationMinutes,
        String courseTitle,
        String status,
        java.math.BigDecimal attendanceIncome
    ) {
    }
}

package com.teacherfeedback.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public final class TeacherFeedbackDtos {
    private TeacherFeedbackDtos() {
    }

    public record CreateStudentRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 30) String grade,
        @NotBlank @Size(max = 30) String subject,
        @Size(max = 255) String learningGoal,
        @Size(max = 255) String address,
        @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal lessonFee
    ) {
        public CreateStudentRequest(String name, String grade, String subject, String learningGoal, String address) {
            this(name, grade, subject, learningGoal, address, null);
        }
        public CreateStudentRequest(String name, String grade, String subject, String learningGoal) {
            this(name, grade, subject, learningGoal, null);
        }
    }

    public record UpdateStudentRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 30) String grade,
        @NotBlank @Size(max = 30) String subject,
        @Size(max = 255) String learningGoal,
        @Size(max = 255) String address,
        @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal lessonFee,
        Boolean clearLessonFee
    ) {
        public UpdateStudentRequest(String name, String grade, String subject, String learningGoal, String address, BigDecimal lessonFee) {
            this(name, grade, subject, learningGoal, address, lessonFee, false);
        }
        public UpdateStudentRequest(String name, String grade, String subject, String learningGoal, String address) {
            this(name, grade, subject, learningGoal, address, null, false);
        }
        public UpdateStudentRequest(String name, String grade, String subject, String learningGoal) {
            this(name, grade, subject, learningGoal, null);
        }
    }

    public record StudentGraduationRequest(@NotNull Boolean graduated) {
    }

    public record CreateLessonRequest(
        @NotBlank @Size(max = 120) String courseTitle,
        @NotNull @Min(15) @Max(360) Integer durationMinutes,
        @NotBlank String content,
        String performance,
        String homework,
        @PastOrPresent LocalDate lessonDate,
        @Pattern(regexp = "professional|encouraging|direct") String feedbackTone,
        @Pattern(regexp = "brief|standard|detailed") String feedbackLength,
        Long analysisId,
        Long scheduledLessonId
    ) {
        public CreateLessonRequest(String courseTitle, Integer durationMinutes, String content,
                                   String performance, String homework, LocalDate lessonDate,
                                   String feedbackTone, String feedbackLength, Long analysisId) {
            this(courseTitle, durationMinutes, content, performance, homework, lessonDate,
                feedbackTone, feedbackLength, analysisId, null);
        }
    }

    public record UpdateLessonRequest(
        @NotBlank @Size(max = 120) String courseTitle,
        @NotNull @Min(15) @Max(360) Integer durationMinutes,
        @NotBlank String content,
        String performance,
        String homework,
        @NotNull @PastOrPresent LocalDate lessonDate
    ) {
    }

    public record UpdateFeedbackRequest(
        @NotBlank @Size(max = 10000) String content
    ) {
    }

    public record RegenerateFeedbackRequest(
        @Pattern(regexp = "professional|encouraging|direct") String feedbackTone,
        @Pattern(regexp = "brief|standard|detailed") String feedbackLength
    ) {
    }

    public record SaveLessonDraftRequest(
        @Size(max = 120) String courseTitle,
        @Min(15) @Max(360) Integer durationMinutes,
        String content,
        String performance,
        String homework,
        @PastOrPresent LocalDate lessonDate,
        Long analysisId
    ) {
    }

    public record AnalyzeTextRequest(
        @NotBlank @Size(max = 30000) String text,
        @Pattern(regexp = "professional|encouraging|direct") String feedbackTone,
        @Pattern(regexp = "brief|standard|detailed") String feedbackLength,
        @Pattern(regexp = "voice|pasted") String sourceType
    ) {
    }

    public record StartLessonSessionRequest(
        @NotNull Long studentId,
        @NotBlank @Size(max = 120) String courseTitle
    ) {
    }

    public record AppendLessonSessionSegmentRequest(
        @NotBlank @Size(max = 80) String clientSegmentId,
        @Min(0) int sequenceNumber,
        @Min(1) @Max(3600) int durationSeconds,
        @Size(max = 30000) String transcript
    ) {
    }

    public record UploadLessonSessionAudioRequest(
        @NotBlank @Size(max = 80) String clientSegmentId,
        @Min(0) int sequenceNumber,
        @Min(1) @Max(3600) int durationSeconds
    ) {
    }

    public record UpdateLessonSessionCaptureRequest(
        @Min(0) @Max(43200) Integer capturedSeconds,
        Boolean confirmPartialAudio
    ) {
    }

    public record AsrHotwordResponse(
        Long studentId,
        List<String> automaticTerms,
        List<String> systemTerms,
        List<String> effectiveTerms
    ) {
    }

    public record AsrEvaluationResponse(
        Long id,
        Long studentId,
        String studentName,
        String originalFilename,
        String courseTitle,
        String referenceText,
        String recognizedText,
        int referenceCharacters,
        int recognizedCharacters,
        int substitutions,
        int deletions,
        int insertions,
        double characterErrorRate,
        double inferenceSeconds,
        List<String> hotwords,
        LocalDateTime createdAt
    ) {
    }

    public record LessonSummary(Long id, String title, LocalDate date, String summary) {
    }

    public record StudentResponse(
        Long id,
        String name,
        String grade,
        String subject,
        String goal,
        String status,
        LocalDate lastLessonDate,
        LocalDateTime updatedAt,
        List<LessonSummary> records,
        String draftTitle,
        LocalDateTime draftUpdatedAt,
        Long latestFeedbackId,
        LocalDateTime latestFeedbackConfirmedAt,
        String address,
        boolean graduated,
        BigDecimal lessonFee
    ) {
    }

    public record LessonResponse(
        Long id,
        Long studentId,
        String courseTitle,
        Integer durationMinutes,
        String content,
        String performance,
        String homework,
        LocalDate lessonDate,
        LocalDateTime createdAt,
        java.time.LocalTime startTime
    ) {
    }

    public record FeedbackResponse(
        Long id,
        Long lessonRecordId,
        String courseTitle,
        String content,
        String generationSource,
        LocalDateTime createdAt,
        LocalDateTime revisedAt,
        LocalDateTime confirmedAt
    ) {
    }

    public record GrowthReportResponse(
        Long id,
        Long studentId,
        Integer lessonCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        String content,
        LocalDateTime createdAt
    ) {
    }

    public record LessonCreationResponse(LessonResponse lessonRecord, FeedbackResponse feedback) {
    }

    public record LessonDraftResponse(
        Long studentId,
        String courseTitle,
        Integer durationMinutes,
        String content,
        String performance,
        String homework,
        LocalDate lessonDate,
        Long analysisId,
        LessonAnalysisResponse analysis,
        LocalDateTime updatedAt
    ) {
    }

    public record LessonSessionSegmentResponse(
        Long id,
        String clientSegmentId,
        int sequenceNumber,
        int durationSeconds,
        String transcript,
        String asrStatus,
        String audioQuality,
        Double audioRms,
        Double clippingRatio,
        Double asrInferenceSeconds,
        LocalDateTime createdAt
    ) {
    }

    public record LessonSessionResponse(
        Long id,
        Long studentId,
        String studentName,
        String courseTitle,
        String status,
        LocalDateTime startedAt,
        LocalDateTime resumedAt,
        LocalDateTime updatedAt,
        int capturedSeconds,
        Integer clientCapturedSeconds,
        List<LessonSessionSegmentResponse> segments
    ) {
    }

    public record LessonDialogueSegmentResponse(
        int speaker,
        int startMs,
        int endMs,
        String text
    ) {
    }

    public record LessonDialogueTranscriptResponse(
        String text,
        List<LessonDialogueSegmentResponse> segments,
        double inferenceSeconds,
        boolean speakerSeparationReliable
    ) {
    }

    public record LessonAnalysisResponse(
        Long id,
        Long studentId,
        String ocrText,
        String courseTitle,
        Integer durationMinutes,
        String content,
        String performance,
        String homework,
        String feedbackDraft,
        List<String> missingFields,
        BigDecimal confidence,
        boolean studentMatched,
        List<String> detectedStudentNames,
        List<String> warnings,
        LocalDateTime createdAt,
        boolean sourceImageStored
    ) {
    }
}

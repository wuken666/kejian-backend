package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.client.OcrClient;
import com.teacherfeedback.api.dao.LessonAnalysisRepository;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonAnalysisResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.SaveLessonDraftRequest;
import com.teacherfeedback.api.model.LessonAnalysis;
import com.teacherfeedback.api.model.LessonDraft;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class LessonAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(LessonAnalysisService.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_BATCH_BYTES = 30L * 1024 * 1024;
    private static final int MAX_IMAGE_COUNT = 6;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private final StudentRepository studentRepository;
    private final LessonAnalysisRepository lessonAnalysisRepository;
    private final LessonDraftRepository lessonDraftRepository;
    private final LessonRecordRepository lessonRecordRepository;
    private final OcrClient ocrClient;
    private final LessonStructuringClient lessonStructuringClient;

    public LessonAnalysisService(
        StudentRepository studentRepository,
        LessonAnalysisRepository lessonAnalysisRepository,
        LessonDraftRepository lessonDraftRepository,
        LessonRecordRepository lessonRecordRepository,
        OcrClient ocrClient,
        LessonStructuringClient lessonStructuringClient
    ) {
        this.studentRepository = studentRepository;
        this.lessonAnalysisRepository = lessonAnalysisRepository;
        this.lessonDraftRepository = lessonDraftRepository;
        this.lessonRecordRepository = lessonRecordRepository;
        this.ocrClient = ocrClient;
        this.lessonStructuringClient = lessonStructuringClient;
    }

    @Transactional
    public LessonAnalysisResponse analyze(
        Long studentId,
        MultipartFile image,
        String feedbackTone,
        String feedbackLength
    ) {
        return analyze(
            studentId,
            image == null ? List.of() : List.of(image),
            feedbackTone,
            feedbackLength
        );
    }

    @Transactional
    public LessonAnalysisResponse analyze(
        Long studentId,
        List<MultipartFile> images,
        String feedbackTone,
        String feedbackLength
    ) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        validateImages(images);
        FeedbackPreferences preferences = preferences(feedbackTone, feedbackLength);

        long startedAt = System.nanoTime();
        log.info(
            "lesson_analysis phase=start image_count={} total_image_bytes={}",
            images.size(),
            images.stream().mapToLong(MultipartFile::getSize).sum()
        );
        try {
            String recognizedText = images.size() == 1
                ? recognize(images.getFirst())
                : recognizeBatch(images);
            // 截图识别已经拿到了 OCR 原文；AI 暂时不可用时也要保留原文进入老师核对页。
            LessonAnalysisResponse response = structureTextAndSave(
                studentId,
                student,
                recognizedText,
                preferences,
                false
            );
            log.info(
                "lesson_analysis phase=success image_count={} duration_ms={} combined_ocr_chars={}",
                images.size(),
                elapsedMillis(startedAt),
                recognizedText.length()
            );
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                "lesson_analysis phase=failure image_count={} duration_ms={} error_type={}",
                images.size(),
                elapsedMillis(startedAt),
                exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    @Transactional
    public LessonAnalysisResponse analyzeText(
        Long studentId,
        String sourceText,
        String feedbackTone,
        String feedbackLength
    ) {
        return analyzeText(studentId, sourceText, feedbackTone, feedbackLength, null);
    }

    @Transactional
    public LessonAnalysisResponse analyzeText(
        Long studentId,
        String sourceText,
        String feedbackTone,
        String feedbackLength,
        String sourceType
    ) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        String text = normalizedText(sourceText, "请粘贴课堂聊天文字");
        FeedbackPreferences preferences = preferences(feedbackTone, feedbackLength);
        boolean voiceTranscript = "voice".equals(sourceType);
        LessonAnalysis reusable = reusableAnalysis(studentId, text, preferences);
        if (reusable != null) {
            saveAnalysisDraft(student, reusable);
            log.info(
                "lesson_analysis phase=reuse source={} analysis_id={} input_chars={}",
                voiceTranscript ? "voice" : "text",
                reusable.getId(),
                text.length()
            );
            return toResponse(reusable);
        }

        long startedAt = System.nanoTime();
        log.info(
            "lesson_analysis phase=start source={} input_chars={}",
            voiceTranscript ? "voice" : "text",
            text.length()
        );
        try {
            LessonAnalysisResponse response = structureTextAndSave(
                studentId,
                student,
                text,
                preferences,
                voiceTranscript
            );
            log.info(
                "lesson_analysis phase=success source={} duration_ms={} input_chars={}",
                voiceTranscript ? "voice" : "text",
                elapsedMillis(startedAt),
                text.length()
            );
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                "lesson_analysis phase=failure source={} duration_ms={} error_type={}",
                voiceTranscript ? "voice" : "text",
                elapsedMillis(startedAt),
                exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    // 语音或粘贴文字已有有效原文时，AI 整理失败也保留原文并进入人工复核流程。
    private LessonAnalysisResponse structureTextAndSave(
        Long studentId,
        Student student,
        String sourceText,
        FeedbackPreferences preferences,
        boolean voiceTranscript
    ) {
        LessonContentQuality.requireMeaningfulSource(sourceText);
        StructuredLesson structuredLesson;
        try {
            structuredLesson = lessonStructuringClient.structure(
                student,
                sourceText,
                recentLessons(studentId),
                preferences
            );
            LessonContentQuality.requireStructuredResult(sourceText, structuredLesson);
            if (voiceTranscript) {
                structuredLesson = VoiceTranscriptPolicy.apply(sourceText, structuredLesson);
            }
        } catch (RuntimeException exception) {
            log.warn(
                "lesson_analysis phase=fallback source=text input_chars={} error_type={}",
                sourceText.length(),
                exception.getClass().getSimpleName()
            );
            structuredLesson = rawTextFallback(student, sourceText);
        }
        LessonAnalysis analysis = lessonAnalysisRepository.save(
            new LessonAnalysis(student, sourceText, structuredLesson, preferences)
        );
        saveAnalysisDraft(student, analysis);
        return toResponse(analysis);
    }

    // AI 没有产出时只保留可核对的原始事实，不臆造学生表现或课后练习。
    private StructuredLesson rawTextFallback(Student student, String sourceText) {
        return new StructuredLesson(
            student.getSubject() + "课堂",
            null,
            sourceText,
            "",
            "",
            "",
            List.of("durationMinutes", "performance", "homework"),
            new java.math.BigDecimal("0.250"),
            true,
            List.of(student.getName()),
            List.of("AI 未提取到结构化信息，已将原始转写保留为课堂内容。")
        );
    }

    private LessonAnalysisResponse structureAndSave(
        Long studentId,
        Student student,
        String sourceText,
        FeedbackPreferences preferences
    ) {
        LessonContentQuality.requireMeaningfulSource(sourceText);
        List<LessonRecord> recentLessons = recentLessons(studentId);
        StructuredLesson structuredLesson = lessonStructuringClient.structure(
            student,
            sourceText,
            recentLessons,
            preferences
        );
        LessonContentQuality.requireStructuredResult(sourceText, structuredLesson);
        LessonAnalysis analysis = lessonAnalysisRepository.save(
            new LessonAnalysis(student, sourceText, structuredLesson, preferences)
        );
        return toResponse(analysis);
    }

    private String recognizeBatch(List<MultipartFile> images) {
        StringBuilder combinedText = new StringBuilder();
        for (int index = 0; index < images.size(); index++) {
            String ocrText = recognize(images.get(index));
            if (!combinedText.isEmpty()) combinedText.append("\n\n");
            combinedText
                .append("【第 ")
                .append(index + 1)
                .append(" 张截图】\n")
                .append(ocrText);
        }
        return combinedText.toString();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    @Transactional
    public LessonAnalysisResponse reanalyze(
        Long studentId,
        Long sourceAnalysisId,
        String feedbackTone,
        String feedbackLength
    ) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        LessonAnalysis source = lessonAnalysisRepository.findByIdAndStudentId(sourceAnalysisId, studentId)
            .orElseGet(() -> {
                // 兼容未经过 HTTP 鉴权的旧单元调用；正式请求始终走按学生归属的查询。
                if (TeacherContext.hasCurrentTeacher()) {
                    throw new ResponseStatusException(NOT_FOUND, "截图分析记录不存在");
                }
                return lessonAnalysisRepository.findById(sourceAnalysisId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "截图分析记录不存在"));
            });
        if (source.getConfirmedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "已生成课堂记录的分析不能重新归属");
        }

        FeedbackPreferences preferences = preferences(feedbackTone, feedbackLength);
        StructuredLesson structuredLesson = lessonStructuringClient.structure(
            student,
            source.getOcrText(),
            recentLessons(studentId),
            preferences
        );
        LessonAnalysis replacement = lessonAnalysisRepository.save(
            new LessonAnalysis(student, source.getOcrText(), structuredLesson, preferences)
        );
        lessonAnalysisRepository.delete(source);
        return toResponse(replacement);
    }

    @Transactional
    public LessonAnalysisResponse correctRecognizedText(
        Long studentId,
        Long sourceAnalysisId,
        String correctedText,
        String feedbackTone,
        String feedbackLength
    ) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        LessonAnalysis source = lessonAnalysisRepository
            .findByIdAndStudentId(sourceAnalysisId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "截图分析记录不存在"));
        if (source.getConfirmedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "已生成课堂记录的分析不能再修改");
        }
        String text = normalizedText(correctedText, "识别原文不能为空");
        FeedbackPreferences preferences = preferences(feedbackTone, feedbackLength);

        long startedAt = System.nanoTime();
        log.info("lesson_analysis phase=start source=corrected_text input_chars={}", text.length());
        try {
            LessonAnalysisResponse replacement = structureAndSave(
                studentId,
                student,
                text,
                preferences
            );
            lessonAnalysisRepository.delete(source);
            log.info(
                "lesson_analysis phase=success source=corrected_text duration_ms={} input_chars={}",
                elapsedMillis(startedAt),
                text.length()
            );
            return replacement;
        } catch (RuntimeException exception) {
            log.warn(
                "lesson_analysis phase=failure source=corrected_text duration_ms={} error_type={}",
                elapsedMillis(startedAt),
                exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private List<LessonRecord> recentLessons(Long studentId) {
        return lessonRecordRepository.findTop3ByStudentIdOrderByLessonDateDescIdDesc(studentId);
    }

    // 手机弱网重试时复用已成功生成但尚未确认的结果，避免重复等待大模型并产生重复记录。
    private LessonAnalysis reusableAnalysis(
        Long studentId,
        String sourceText,
        FeedbackPreferences preferences
    ) {
        return lessonAnalysisRepository
            .findTop5ByStudentIdAndOcrTextAndFeedbackToneAndFeedbackLengthAndConfirmedAtIsNullOrderByCreatedAtDesc(
                studentId,
                sourceText,
                preferences.tone(),
                preferences.length()
            )
            .stream()
            .filter(analysis -> !analysis.getFeedbackDraft().isBlank())
            .findFirst()
            .orElse(null);
    }

    // AI 成功后先在服务端保存可恢复草稿，即使手机连接中断，重新打开也能继续确认而不必再次生成。
    private void saveAnalysisDraft(Student student, LessonAnalysis analysis) {
        SaveLessonDraftRequest request = new SaveLessonDraftRequest(
            analysis.getCourseTitle(),
            analysis.getDurationMinutes(),
            analysis.getContent(),
            analysis.getPerformance(),
            analysis.getHomework(),
            LocalDate.now(),
            analysis.getId()
        );
        LessonDraft draft = lessonDraftRepository.findByStudentId(student.getId())
            .orElseGet(() -> new LessonDraft(student, request));
        draft.update(request);
        lessonDraftRepository.save(draft);
    }

    private FeedbackPreferences preferences(String tone, String length) {
        try {
            return FeedbackPreferences.fromNullable(tone, length);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private String normalizedText(String sourceText, String emptyMessage) {
        if (sourceText == null || sourceText.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, emptyMessage);
        }
        String text = sourceText.strip();
        if (text.length() > 30000) {
            throw new ResponseStatusException(BAD_REQUEST, "识别或粘贴文字不能超过 30000 字");
        }
        return text;
    }

    private String recognize(MultipartFile image) {
        byte[] imageBytes = null;
        try {
            imageBytes = image.getBytes();
            return ocrClient.recognize(imageBytes);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "图片读取失败，请重新选择", exception);
        } finally {
            if (imageBytes != null) Arrays.fill(imageBytes, (byte) 0);
        }
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择课堂反馈截图");
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            throw new ResponseStatusException(BAD_REQUEST, "一次最多识别 6 张截图");
        }
        long totalBytes = 0;
        for (MultipartFile image : images) {
            validateImage(image);
            totalBytes += image.getSize();
        }
        if (totalBytes > MAX_BATCH_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "截图总大小不能超过 30MB");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "截图不能为空");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "单张截图不能超过 10MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new ResponseStatusException(BAD_REQUEST, "仅支持 JPG、PNG 或 WebP 图片");
        }
    }

    private LessonAnalysisResponse toResponse(LessonAnalysis analysis) {
        return new LessonAnalysisResponse(
            analysis.getId(),
            analysis.getStudent().getId(),
            analysis.getOcrText(),
            analysis.getCourseTitle(),
            analysis.getDurationMinutes(),
            analysis.getContent(),
            analysis.getPerformance(),
            analysis.getHomework(),
            analysis.getFeedbackDraft(),
            analysis.getMissingFields(),
            analysis.getConfidence(),
            analysis.isStudentMatched(),
            analysis.getDetectedStudentNames(),
            analysis.getWarnings(),
            analysis.getCreatedAt(),
            false
        );
    }
}

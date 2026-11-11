package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import com.teacherfeedback.api.client.OcrClient;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.dao.LessonAnalysisRepository;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonAnalysisResponse;
import com.teacherfeedback.api.model.LessonAnalysis;
import com.teacherfeedback.api.model.LessonDraft;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class LessonAnalysisServiceTests {
    private static final FeedbackPreferences DEFAULT_PREFERENCES =
        FeedbackPreferences.fromNullable(null, null);

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LessonAnalysisRepository lessonAnalysisRepository;
    @Mock
    private LessonDraftRepository lessonDraftRepository;
    @Mock
    private LessonRecordRepository lessonRecordRepository;
    @Mock
    private OcrClient ocrClient;
    @Mock
    private LessonStructuringClient lessonStructuringClient;

    @Test
    void storesOnlyRecognizedTextAndStructuredFeedback() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        byte[] imageBytes = {1, 2, 3, 4};
        MockMultipartFile image = new MockMultipartFile(
            "image", "lesson.png", "image/png", imageBytes
        );
        StructuredLesson structuredLesson = new StructuredLesson(
            "暑期英语复习",
            null,
            "复习名词和易混词组",
            "整体正确率较高",
            "背诵新单词 10 个",
            "今天复习了名词和易混词组，整体掌握良好。",
            List.of("durationMinutes"),
            new BigDecimal("0.920"),
            true,
            List.of("小雨"),
            List.of()
        );

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(ocrClient.recognize(any(byte[].class))).thenAnswer(invocation -> {
            assertArrayEquals(imageBytes, invocation.getArgument(0));
            return "OCR 识别文字";
        });
        when(lessonStructuringClient.structure(
            student,
            "OCR 识别文字",
            List.of(),
            DEFAULT_PREFERENCES
        ))
            .thenReturn(structuredLesson);
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().analyze(1L, image, null, null);

        assertFalse(response.sourceImageStored());
        verify(lessonAnalysisRepository).save(any(LessonAnalysis.class));
        assertFalse(List.of(LessonAnalysis.class.getDeclaredFields()).stream()
            .anyMatch(field -> field.getType().equals(byte[].class)));
    }

    @Test
    void rejectsUnsupportedFilesBeforeCallingExternalServices() {
        when(studentRepository.findById(1L))
            .thenReturn(Optional.of(new Student("小雨", "初二", "英语", "巩固语法")));
        MockMultipartFile file = new MockMultipartFile(
            "image", "lesson.pdf", "application/pdf", new byte[] {1}
        );

        assertThrows(
            ResponseStatusException.class,
            () -> service().analyze(1L, file, null, null)
        );

        verify(ocrClient, never()).recognize(any());
        verify(lessonStructuringClient, never()).structure(any(), any(), any(), any());
        verify(lessonAnalysisRepository, never()).save(any());
    }

    @Test
    void combinesMultipleScreenshotsInSelectionOrderAndCallsAiOnce() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        MockMultipartFile first = new MockMultipartFile(
            "images", "01.png", "image/png", new byte[] {1}
        );
        MockMultipartFile second = new MockMultipartFile(
            "images", "02.png", "image/png", new byte[] {2}
        );
        String combinedText = "【第 1 张截图】\n第一张文字\n\n【第 2 张截图】\n第二张文字";

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(ocrClient.recognize(any(byte[].class)))
            .thenReturn("第一张文字", "第二张文字");
        when(lessonStructuringClient.structure(
            student,
            combinedText,
            List.of(),
            DEFAULT_PREFERENCES
        ))
            .thenReturn(structuredLesson(true, List.of("小雨")));
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().analyze(
            1L,
            List.of(first, second),
            null,
            null
        );

        assertEquals(combinedText, response.ocrText());
        verify(ocrClient, times(2)).recognize(any(byte[].class));
        verify(lessonStructuringClient).structure(
            student,
            combinedText,
            List.of(),
            DEFAULT_PREFERENCES
        );
        verify(lessonAnalysisRepository).save(any(LessonAnalysis.class));
    }

    @Test
    void keepsOcrTextWhenScreenshotAiAnalysisFails() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        MockMultipartFile image = new MockMultipartFile(
            "image", "lesson.png", "image/png", new byte[] {1, 2}
        );
        String ocrText = "本节复习一般过去时，学生完成了句型练习。";

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(ocrClient.recognize(any(byte[].class))).thenReturn(ocrText);
        when(lessonStructuringClient.structure(
            student,
            ocrText,
            List.of(),
            DEFAULT_PREFERENCES
        )).thenThrow(new ResponseStatusException(BAD_GATEWAY, "AI 服务暂不可用"));
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().analyze(1L, image, null, null);

        assertEquals(ocrText, response.ocrText());
        assertEquals(ocrText, response.content());
        assertTrue(response.missingFields().contains("performance"));
        assertTrue(response.missingFields().contains("homework"));
        verify(lessonAnalysisRepository).save(any(LessonAnalysis.class));
    }

    @Test
    void analyzesPastedTextWithoutCallingOcr() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        String sourceText = "今天复习了定语从句，小雨长句拆分有进步，作业是完成练习册第 12 页。";
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonStructuringClient.structure(
            student,
            sourceText,
            List.of(),
            DEFAULT_PREFERENCES
        ))
            .thenReturn(structuredLesson(true, List.of("小雨")));
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().analyzeText(
            1L,
            "  " + sourceText + "  ",
            null,
            null
        );

        assertEquals(sourceText, response.ocrText());
        verify(ocrClient, never()).recognize(any());
        verify(lessonStructuringClient).structure(
            student,
            sourceText,
            List.of(),
            DEFAULT_PREFERENCES
        );
        verify(lessonAnalysisRepository).save(any(LessonAnalysis.class));
    }

    @Test
    void keepsRawTranscriptAsLessonContentWhenAiCannotExtractFields() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        String transcript = "本节课讨论了关系代词的用法，但没有识别出更明确的课堂结构。";
        StructuredLesson emptyResult = new StructuredLesson(
            "",
            null,
            "",
            "",
            "",
            "",
            List.of("courseTitle", "durationMinutes", "content", "performance", "homework"),
            new BigDecimal("0.100"),
            true,
            List.of(),
            List.of()
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonStructuringClient.structure(
            student,
            transcript,
            List.of(),
            DEFAULT_PREFERENCES
        )).thenReturn(emptyResult);
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().analyzeText(1L, transcript, null, null);

        assertEquals(transcript, response.content());
        assertEquals("英语课堂", response.courseTitle());
        assertTrue(response.missingFields().contains("performance"));
        assertTrue(response.missingFields().contains("homework"));
        verify(lessonAnalysisRepository).save(any(LessonAnalysis.class));
    }

    @Test
    void appliesSavedFeedbackPreferencesToTextAnalysis() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        String sourceText = "本节复习定语从句，课堂参与积极。";
        FeedbackPreferences preferences = new FeedbackPreferences("encouraging", "brief");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonStructuringClient.structure(
            student,
            sourceText,
            List.of(),
            preferences
        )).thenReturn(structuredLesson(true, List.of("小雨")));
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service().analyzeText(1L, sourceText, "encouraging", "brief");

        verify(lessonStructuringClient).structure(student, sourceText, List.of(), preferences);
        verify(lessonAnalysisRepository).save(argThat(analysis ->
            "encouraging".equals(analysis.getFeedbackTone())
                && "brief".equals(analysis.getFeedbackLength())
        ));
    }

    @Test
    void reusesSuccessfulUnconfirmedAnalysisAfterMobileRetry() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        String sourceText = "本节复习定语从句，学生能够识别关系代词，长句拆分仍需加强。";
        FeedbackPreferences preferences = new FeedbackPreferences("professional", "brief");
        LessonAnalysis existing = new LessonAnalysis(
            student,
            sourceText,
            structuredLesson(true, List.of("小雨")),
            preferences
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonAnalysisRepository
            .findTop5ByStudentIdAndOcrTextAndFeedbackToneAndFeedbackLengthAndConfirmedAtIsNullOrderByCreatedAtDesc(
                1L,
                sourceText,
                "professional",
                "brief"
            ))
            .thenReturn(List.of(existing));

        LessonAnalysisResponse response = service().analyzeText(
            1L,
            sourceText,
            "professional",
            "brief",
            "voice"
        );

        assertEquals(existing.getContent(), response.content());
        verify(lessonStructuringClient, never()).structure(any(), any(), any(), any());
        verify(lessonAnalysisRepository, never()).save(any());
        verify(lessonDraftRepository).save(any(LessonDraft.class));
    }

    @Test
    void rejectsMoreThanSixScreenshotsBeforeCallingExternalServices() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        MockMultipartFile image = new MockMultipartFile(
            "images", "lesson.png", "image/png", new byte[] {1}
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        assertThrows(
            ResponseStatusException.class,
            () -> service().analyze(
                1L,
                List.of(image, image, image, image, image, image, image),
                null,
                null
            )
        );

        verify(ocrClient, never()).recognize(any());
        verify(lessonStructuringClient, never()).structure(any(), any(), any(), any());
        verify(lessonAnalysisRepository, never()).save(any());
    }

    @Test
    void reanalyzesSavedOcrTextWithoutCallingOcrAgain() {
        Student originalStudent = new Student("小雨", "初二", "英语", "巩固语法");
        Student targetStudent = new Student("思涵", "初二", "英语", "巩固语法");
        LessonAnalysis source = new LessonAnalysis(
            originalStudent,
            "已经保存的 OCR 文字",
            structuredLesson(false, List.of("思涵")),
            DEFAULT_PREFERENCES
        );
        StructuredLesson replacement = structuredLesson(true, List.of("思涵"));

        when(studentRepository.findById(2L)).thenReturn(Optional.of(targetStudent));
        when(lessonAnalysisRepository.findById(7L)).thenReturn(Optional.of(source));
        when(lessonStructuringClient.structure(
            targetStudent,
            "已经保存的 OCR 文字",
            List.of(),
            DEFAULT_PREFERENCES
        ))
            .thenReturn(replacement);
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().reanalyze(2L, 7L, null, null);

        assertFalse(response.sourceImageStored());
        verify(ocrClient, never()).recognize(any());
        verify(lessonStructuringClient).structure(
            targetStudent,
            "已经保存的 OCR 文字",
            List.of(),
            DEFAULT_PREFERENCES
        );
        verify(lessonAnalysisRepository).delete(source);
    }

    @Test
    void correctsRecognizedTextWithoutCallingOcrAgainAndReplacesOldAnalysis() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonAnalysis source = new LessonAnalysis(
            student,
            "错误的 OCR 页码 21",
            structuredLesson(true, List.of("小雨")),
            DEFAULT_PREFERENCES
        );
        String correctedText = "正确的 OCR 页码 12";
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonAnalysisRepository.findByIdAndStudentId(7L, 1L))
            .thenReturn(Optional.of(source));
        when(lessonStructuringClient.structure(
            student,
            correctedText,
            List.of(),
            DEFAULT_PREFERENCES
        )).thenReturn(structuredLesson(true, List.of("小雨")));
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonAnalysisResponse response = service().correctRecognizedText(
            1L,
            7L,
            correctedText,
            null,
            null
        );

        assertEquals(correctedText, response.ocrText());
        verify(ocrClient, never()).recognize(any());
        verify(lessonAnalysisRepository).delete(source);
    }

    @Test
    void correctionFailureKeepsOldAnalysis() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonAnalysis source = new LessonAnalysis(
            student,
            "原 OCR 文字",
            structuredLesson(true, List.of("小雨")),
            DEFAULT_PREFERENCES
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonAnalysisRepository.findByIdAndStudentId(7L, 1L))
            .thenReturn(Optional.of(source));
        when(lessonStructuringClient.structure(
            student,
            "修正后的文字",
            List.of(),
            DEFAULT_PREFERENCES
        )).thenThrow(new ResponseStatusException(BAD_GATEWAY, "AI 服务暂不可用"));

        assertThrows(
            ResponseStatusException.class,
            () -> service().correctRecognizedText(
                1L,
                7L,
                "修正后的文字",
                null,
                null
            )
        );

        verify(lessonAnalysisRepository, never()).delete(source);
    }

    @Test
    void passesOnlyThreeMostRecentLessonsToAi() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        String sourceText = "本节复习定语从句，长句拆分更熟练。";
        List<LessonRecord> expectedHistory = List.of(
            lesson(student, "关系代词", LocalDate.of(2026, 7, 22)),
            lesson(student, "长句拆分", LocalDate.of(2026, 7, 20)),
            lesson(student, "从句基础", LocalDate.of(2026, 7, 18))
        );

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findTop3ByStudentIdOrderByLessonDateDescIdDesc(1L))
            .thenReturn(expectedHistory);
        when(lessonStructuringClient.structure(
            student,
            sourceText,
            expectedHistory,
            DEFAULT_PREFERENCES
        ))
            .thenReturn(structuredLesson(true, List.of("小雨")));
        when(lessonAnalysisRepository.save(any(LessonAnalysis.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service().analyzeText(1L, sourceText, null, null);

        verify(lessonRecordRepository).findTop3ByStudentIdOrderByLessonDateDescIdDesc(1L);
        verify(lessonStructuringClient).structure(
            student,
            sourceText,
            expectedHistory,
            DEFAULT_PREFERENCES
        );
    }

    private LessonRecord lesson(Student student, String title, LocalDate date) {
        return new LessonRecord(student, title, 60, title, "掌握稳定", "复习", date);
    }

    private StructuredLesson structuredLesson(boolean studentMatched, List<String> names) {
        return new StructuredLesson(
            "暑期英语复习",
            60,
            "复习名词和易混词组",
            studentMatched ? "课堂参与积极" : "",
            "背诵新单词 10 个",
            studentMatched ? "今天复习了名词和易混词组。" : "",
            studentMatched ? List.of() : List.of("performance"),
            new BigDecimal("0.920"),
            studentMatched,
            names,
            List.of()
        );
    }

    private LessonAnalysisService service() {
        return new LessonAnalysisService(
            studentRepository,
            lessonAnalysisRepository,
            lessonDraftRepository,
            lessonRecordRepository,
            ocrClient,
            lessonStructuringClient
        );
    }
}

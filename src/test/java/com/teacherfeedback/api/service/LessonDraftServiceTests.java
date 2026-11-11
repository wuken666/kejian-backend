package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dao.LessonAnalysisRepository;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonDraftResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.SaveLessonDraftRequest;
import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.model.LessonAnalysis;
import com.teacherfeedback.api.model.LessonDraft;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonDraftServiceTests {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LessonDraftRepository lessonDraftRepository;
    @Mock
    private LessonAnalysisRepository lessonAnalysisRepository;

    @Test
    void createsAndRestoresTrimmedDraftContent() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        SaveLessonDraftRequest request = new SaveLessonDraftRequest(
            " 定语从句复习 ",
            60,
            " 复习关系代词 ",
            " 长句分析需要加强 ",
            " 完成练习册第 12 页 ",
            LocalDate.of(2026, 7, 22),
            null
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonDraftRepository.findByStudentId(1L)).thenReturn(Optional.empty());
        when(lessonDraftRepository.save(any(LessonDraft.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonDraftResponse response = service().saveDraft(1L, request);

        assertEquals("定语从句复习", response.courseTitle());
        assertEquals(LocalDate.of(2026, 7, 22), response.lessonDate());
        assertEquals("复习关系代词", response.content());
        assertEquals("长句分析需要加强", response.performance());
        assertEquals("完成练习册第 12 页", response.homework());
    }

    @Test
    void rejectsDraftLinkedToMissingAnalysis() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonAnalysisRepository.findByIdAndStudentId(99L, 1L))
            .thenReturn(Optional.empty());

        assertThrows(
            ResponseStatusException.class,
            () -> service().saveDraft(
                1L,
                new SaveLessonDraftRequest("", 60, "", "", "", LocalDate.now(), 99L)
            )
        );

        verify(lessonDraftRepository, never()).save(any());
    }

    @Test
    void restoresTheSavedAiSummaryWithItsDraft() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonAnalysis analysis = new LessonAnalysis(
            student,
            "已保存的 OCR 文字",
            new StructuredLesson(
                "定语从句复习",
                60,
                "复习关系代词",
                "课堂参与积极",
                "完成练习册第 12 页",
                "可直接发送给家长的反馈。",
                List.of(),
                new BigDecimal("0.92"),
                true,
                List.of("小雨"),
                List.of()
            ),
            FeedbackPreferences.fromNullable(null, null)
        );
        LessonDraft draft = new LessonDraft(
            student,
            new SaveLessonDraftRequest(
                "定语从句复习",
                60,
                "复习关系代词",
                "课堂参与积极",
                "完成练习册第 12 页",
                LocalDate.of(2026, 7, 22),
                7L
            )
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonDraftRepository.findByStudentId(1L)).thenReturn(Optional.of(draft));
        when(lessonAnalysisRepository.findByIdAndStudentId(7L, null))
            .thenReturn(Optional.of(analysis));

        LessonDraftResponse response = service().findDraft(1L).orElseThrow();

        assertEquals("定语从句复习", response.analysis().courseTitle());
        assertEquals(new BigDecimal("0.92"), response.analysis().confidence());
        assertEquals("已保存的 OCR 文字", response.analysis().ocrText());
    }

    private LessonDraftService service() {
        return new LessonDraftService(
            studentRepository,
            lessonDraftRepository,
            lessonAnalysisRepository
        );
    }
}

package com.teacherfeedback.api.model;

import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonAnalysisTests {

    @Test
    void cachedFeedbackMatchesOnlyWhenContentAndStyleAreUnchanged() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        StructuredLesson structuredLesson = new StructuredLesson(
            "定语从句复习",
            60,
            "复习关系代词的选择",
            "课堂参与积极",
            "完成练习册第 12 页",
            "家长版反馈",
            List.of(),
            new BigDecimal("0.950"),
            true,
            List.of("小雨"),
            List.of()
        );
        LessonAnalysis analysis = new LessonAnalysis(
            student,
            "OCR 文字",
            structuredLesson,
            new FeedbackPreferences("professional", "standard")
        );

        assertTrue(analysis.matches(request("professional", "standard")));
        assertFalse(analysis.matches(request("encouraging", "standard")));
        assertFalse(analysis.matches(request("professional", "brief")));
    }

    private CreateLessonRequest request(String feedbackTone, String feedbackLength) {
        return new CreateLessonRequest(
            "定语从句复习",
            60,
            "复习关系代词的选择",
            "课堂参与积极",
            "完成练习册第 12 页",
            LocalDate.of(2026, 7, 23),
            feedbackTone,
            feedbackLength,
            null
        );
    }
}

package com.teacherfeedback.api.client;

import com.teacherfeedback.api.model.Student;
import com.teacherfeedback.api.model.LessonRecord;

import java.math.BigDecimal;
import java.util.List;

public interface LessonStructuringClient {
    StructuredLesson structure(
        Student student,
        String sourceText,
        List<LessonRecord> recentLessons,
        FeedbackPreferences preferences
    );

    String generateFeedback(
        Student student,
        LessonRecord lessonRecord,
        List<LessonRecord> recentLessons,
        FeedbackPreferences preferences
    );

    String generateGrowthReport(Student student, List<LessonRecord> lessons);

    record StructuredLesson(
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
        List<String> warnings
    ) {
    }
}

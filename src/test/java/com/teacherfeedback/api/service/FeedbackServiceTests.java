package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.dao.FeedbackRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.FeedbackResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.RegenerateFeedbackRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateFeedbackRequest;
import com.teacherfeedback.api.model.Feedback;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTests {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private LessonRecordRepository lessonRecordRepository;
    @Mock
    private LessonStructuringClient lessonStructuringClient;

    @Test
    void revisesFeedbackOwnedByStudent() {
        Feedback feedback = feedback();
        when(feedbackRepository.findByIdAndLessonRecordStudentId(8L, 2L))
            .thenReturn(Optional.of(feedback));

        FeedbackResponse response = service().updateFeedback(
            2L,
            8L,
            new UpdateFeedbackRequest("  老师确认后的家长反馈。  ")
        );

        assertEquals("老师确认后的家长反馈。", response.content());
        assertEquals("MANUAL", response.generationSource());
        assertNotNull(response.revisedAt());
        assertNotNull(response.confirmedAt());
    }

    @Test
    void persistsTeacherConfirmation() {
        Feedback feedback = feedback();
        when(feedbackRepository.findByIdAndLessonRecordStudentId(8L, 2L))
            .thenReturn(Optional.of(feedback));

        FeedbackResponse response = service().confirmFeedback(2L, 8L);

        assertNotNull(response.confirmedAt());
    }

    @Test
    void rejectsFeedbackFromAnotherStudent() {
        when(feedbackRepository.findByIdAndLessonRecordStudentId(8L, 99L))
            .thenReturn(Optional.empty());

        assertThrows(
            ResponseStatusException.class,
            () -> service().updateFeedback(
                99L,
                8L,
                new UpdateFeedbackRequest("不能修改其他学生的反馈")
            )
        );
    }

    @Test
    void deletesFeedbackOwnedByStudent() {
        Feedback feedback = feedback();
        when(feedbackRepository.findByIdAndLessonRecordStudentId(8L, 2L))
            .thenReturn(Optional.of(feedback));

        service().deleteFeedback(2L, 8L);

        verify(feedbackRepository).delete(feedback);
    }

    @Test
    void regeneratesFromConfirmedLessonAndExcludesCurrentLessonFromHistory() {
        Feedback feedback = feedback();
        feedback.confirm();
        LessonRecord currentLesson = feedback.getLessonRecord();
        Student student = currentLesson.getStudent();
        LessonRecord previousLesson = new LessonRecord(
            student,
            "关系代词",
            60,
            "复习关系代词",
            "长句拆分仍需提示",
            "完成专项练习",
            LocalDate.of(2026, 7, 20)
        );
        FeedbackPreferences preferences = new FeedbackPreferences("encouraging", "brief");
        when(feedbackRepository.findByIdAndLessonRecordStudentId(8L, 2L))
            .thenReturn(Optional.of(feedback));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(2L))
            .thenReturn(List.of(currentLesson, previousLesson));
        when(lessonStructuringClient.generateFeedback(
            student,
            currentLesson,
            List.of(previousLesson),
            preferences
        )).thenReturn("重新生成的温和简短反馈。");

        FeedbackResponse response = service().regenerateFeedback(
            2L,
            8L,
            new RegenerateFeedbackRequest("encouraging", "brief")
        );

        assertEquals("重新生成的温和简短反馈。", response.content());
        assertEquals("REGENERATED", response.generationSource());
        assertNotNull(response.revisedAt());
        assertNull(response.confirmedAt());
        verify(lessonStructuringClient).generateFeedback(
            student,
            currentLesson,
            List.of(previousLesson),
            preferences
        );
    }

    @Test
    void aiFailureDoesNotOverwriteExistingFeedback() {
        Feedback feedback = feedback();
        LessonRecord lesson = feedback.getLessonRecord();
        when(feedbackRepository.findByIdAndLessonRecordStudentId(8L, 2L))
            .thenReturn(Optional.of(feedback));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(2L))
            .thenReturn(List.of(lesson));
        when(lessonStructuringClient.generateFeedback(
            lesson.getStudent(),
            lesson,
            List.of(),
            FeedbackPreferences.fromNullable(null, null)
        )).thenThrow(new ResponseStatusException(BAD_GATEWAY, "AI 服务暂不可用"));

        assertThrows(
            ResponseStatusException.class,
            () -> service().regenerateFeedback(
                2L,
                8L,
                new RegenerateFeedbackRequest(null, null)
            )
        );

        assertEquals("AI 生成的初稿。", feedback.getContent());
        assertEquals("AI", feedback.getGenerationSource().name());
        assertNull(feedback.getRevisedAt());
    }

    private Feedback feedback() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonRecord lesson = new LessonRecord(
            student,
            "定语从句复习",
            60,
            "复习关系代词",
            "课堂参与积极",
            "完成练习册第 12 页",
            LocalDate.of(2026, 7, 23)
        );
        return new Feedback(lesson, "PARENT", "AI 生成的初稿。");
    }

    private FeedbackService service() {
        return new FeedbackService(
            studentRepository,
            feedbackRepository,
            lessonRecordRepository,
            lessonStructuringClient
        );
    }
}

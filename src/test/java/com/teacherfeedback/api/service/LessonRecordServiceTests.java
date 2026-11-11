package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.dao.FeedbackRepository;
import com.teacherfeedback.api.dao.LessonAnalysisRepository;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonCreationResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateLessonRequest;
import com.teacherfeedback.api.model.Feedback;
import com.teacherfeedback.api.model.LessonAnalysis;
import com.teacherfeedback.api.model.LessonRecord;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class LessonRecordServiceTests {
    private static final FeedbackPreferences DEFAULT_PREFERENCES =
        FeedbackPreferences.fromNullable(null, null);

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LessonRecordRepository lessonRecordRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private LessonAnalysisRepository lessonAnalysisRepository;
    @Mock
    private LessonDraftRepository lessonDraftRepository;
    @Mock
    private LessonStructuringClient lessonStructuringClient;

    @Mock
    private com.teacherfeedback.api.dao.ScheduledLessonRepository scheduledLessonRepository;

    @Test
    void listsScheduledStartTimeWithoutInventingLegacyTime() {
        Student student = new Student("小雨", "初二", "英语", "");
        org.springframework.test.util.ReflectionTestUtils.setField(student, "id", 1L);
        var date = LocalDate.of(2026, 11, 5);
        var time = java.time.LocalTime.of(16, 30);
        var linked = new LessonRecord(student, "阅读", 60, "内容", "", "", date);
        linked.linkScheduledLesson(9L);
        var legacy = new LessonRecord(student, "阅读", 60, "内容", "", "", date);
        var scheduled = new com.teacherfeedback.api.model.ScheduledLesson(student, date, time, 60, "阅读");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(1L)).thenReturn(List.of(linked, legacy));
        when(scheduledLessonRepository.findByIdAndTeacherId(9L, student.getTeacherId())).thenReturn(Optional.of(scheduled));
        var service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scheduledLessonRepository", scheduledLessonRepository);
        var records = service.findLessons(1L);
        assertEquals(time, records.get(0).startTime());
        org.junit.jupiter.api.Assertions.assertNull(records.get(1).startTime());
        scheduled.update(student, date.plusDays(1), time, 60, "阅读");
        org.junit.jupiter.api.Assertions.assertNull(service.findLessons(1L).get(0).startTime());
    }

    @Test
    void scheduledRecordRejectsMissingOrWrongStudentCourse() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        org.springframework.test.util.ReflectionTestUtils.setField(student, "id", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        LessonRecordService service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scheduledLessonRepository", scheduledLessonRepository);
        CreateLessonRequest original = request();
        CreateLessonRequest linked = new CreateLessonRequest(original.courseTitle(), 60, original.content(), "", "",
            original.lessonDate(), null, null, null, 9L);
        when(scheduledLessonRepository.findByIdAndTeacherId(9L, student.getTeacherId())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.createLesson(1L, linked));
        Student other = new Student("其他学生", "初二", "英语", "");
        org.springframework.test.util.ReflectionTestUtils.setField(other, "id", 2L);
        var scheduled = new com.teacherfeedback.api.model.ScheduledLesson(other, original.lessonDate(), java.time.LocalTime.NOON, 60, "阅读");
        when(scheduledLessonRepository.findByIdAndTeacherId(9L, student.getTeacherId())).thenReturn(Optional.of(scheduled));
        assertThrows(ResponseStatusException.class, () -> service.createLesson(1L, linked));
        verify(lessonRecordRepository, never()).save(any());
    }

    @Test
    void manualLessonUsesAiGeneratedFeedback() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        CreateLessonRequest request = request();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStructuringClient.generateFeedback(
            any(Student.class),
            any(LessonRecord.class),
            any(),
            any(FeedbackPreferences.class)
        ))
            .thenReturn("这是 AI 根据老师确认内容生成的反馈。");
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonCreationResponse response = service().createLesson(1L, request);

        assertEquals("这是 AI 根据老师确认内容生成的反馈。", response.feedback().content());
        assertEquals("AI", response.feedback().generationSource());
        verify(lessonStructuringClient).generateFeedback(
            any(Student.class),
            any(LessonRecord.class),
            any(),
            org.mockito.ArgumentMatchers.eq(DEFAULT_PREFERENCES)
        );
        verify(lessonDraftRepository).deleteByStudentId(1L);
    }

    @Test
    void manualLessonAppliesTeacherFeedbackPreferences() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        FeedbackPreferences preferences = new FeedbackPreferences("direct", "brief");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStructuringClient.generateFeedback(
            any(Student.class),
            any(LessonRecord.class),
            any(),
            any(FeedbackPreferences.class)
        )).thenReturn("本节重点清晰，下一步完成指定练习。");
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service().createLesson(1L, request("direct", "brief"));

        verify(lessonStructuringClient).generateFeedback(
            org.mockito.ArgumentMatchers.eq(student),
            any(LessonRecord.class),
            org.mockito.ArgumentMatchers.eq(List.of()),
            org.mockito.ArgumentMatchers.eq(preferences)
        );
    }

    @Test
    void aiFailureFallsBackWithoutInventingStudentPerformance() {
        Student student = new Student("小雨", "初二", "英语", "");
        CreateLessonRequest request = request();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStructuringClient.generateFeedback(
            any(Student.class),
            any(LessonRecord.class),
            any(),
            any(FeedbackPreferences.class)
        ))
            .thenThrow(new ResponseStatusException(BAD_GATEWAY, "AI 服务暂不可用"));
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonCreationResponse response = service().createLesson(1L, request);
        String feedback = response.feedback().content();

        assertEquals("FALLBACK", response.feedback().generationSource());
        assertTrue(feedback.contains("课堂参与积极，长句分析还需更细心"));
        assertTrue(feedback.contains("完成练习册第 12 页"));
        assertFalse(feedback.contains("能够回忆并说出"));
        assertFalse(feedback.contains("在老师追问时"));
        assertFalse(feedback.contains("当前学习目标"));
        assertFalse(feedback.contains("暂未填写"));
    }

    @Test
    void unknownPerformanceAndHomeworkStayAbsentFromFallbackFeedback() {
        Student student = new Student("小雨", "初二", "英语", "");
        CreateLessonRequest request = new CreateLessonRequest(
            "一般现在时复习",
            60,
            "复习第三人称单数变化和助动词选择。",
            "",
            "",
            LocalDate.of(2026, 8, 12),
            "professional",
            "brief",
            null
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStructuringClient.generateFeedback(any(), any(), any(), any()))
            .thenThrow(new ResponseStatusException(BAD_GATEWAY, "AI 服务暂不可用"));
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonCreationResponse response = service().createLesson(1L, request);

        assertTrue(response.feedback().content().contains("复习第三人称单数变化和助动词选择"));
        assertFalse(response.feedback().content().contains("课堂表现："));
        assertFalse(response.feedback().content().contains("课后练习："));
    }

    @Test
    void unconfirmedHomeworkFromAiFallsBackToTeacherFacts() {
        Student student = new Student("小雨", "初二", "英语", "");
        CreateLessonRequest request = new CreateLessonRequest(
            "一般现在时复习",
            60,
            "复习第三人称单数变化和助动词选择。",
            "基础判断题完成稳定。",
            "",
            LocalDate.of(2026, 8, 12),
            "professional",
            "brief",
            null
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStructuringClient.generateFeedback(any(), any(), any(), any()))
            .thenReturn("本节复习一般现在时，建议课后完成相关练习。");
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonCreationResponse response = service().createLesson(1L, request);

        assertEquals("FALLBACK", response.feedback().generationSource());
        assertTrue(response.feedback().content().contains("复习第三人称单数变化和助动词选择"));
        assertFalse(response.feedback().content().contains("作业"));
        assertFalse(response.feedback().content().contains("课后练习"));
    }

    @Test
    void unchangedAnalysisDraftIsReusedWithoutAnotherAiCall() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        StructuredLesson structuredLesson = new StructuredLesson(
            "定语从句复习",
            60,
            "复习关系代词的选择",
            "课堂参与积极，长句分析还需更细心",
            "完成练习册第 12 页",
            "截图分析阶段已经生成的家长反馈。",
            List.of(),
            new BigDecimal("0.950"),
            true,
            List.of("小雨"),
            List.of()
        );
        LessonAnalysis analysis = new LessonAnalysis(
            student,
            "截图 OCR 文字",
            structuredLesson,
            DEFAULT_PREFERENCES
        );
        CreateLessonRequest request = request(null, null, 7L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonAnalysisRepository.findByIdAndStudentId(7L, 1L))
            .thenReturn(Optional.of(analysis));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LessonCreationResponse response = service().createLesson(1L, request);

        assertEquals("截图分析阶段已经生成的家长反馈。", response.feedback().content());
        assertEquals("ANALYSIS", response.feedback().generationSource());
        verify(lessonStructuringClient, never()).generateFeedback(any(), any(), any(), any());
    }

    @Test
    void rejectsAnAnalysisThatDoesNotMatchTheSelectedStudent() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonAnalysis analysis = new LessonAnalysis(
            student,
            "另一位学生的课堂截图文字",
            new StructuredLesson(
                "定语从句复习",
                60,
                "复习关系代词的选择",
                "",
                "完成练习册第 12 页",
                "",
                List.of("performance"),
                new BigDecimal("0.90"),
                false,
                List.of("小明"),
                List.of("截图学生与当前学生不一致")
            ),
            DEFAULT_PREFERENCES
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonAnalysisRepository.findByIdAndStudentId(7L, 1L))
            .thenReturn(Optional.of(analysis));

        assertThrows(
            ResponseStatusException.class,
            () -> service().createLesson(1L, request(null, null, 7L))
        );

        verify(lessonRecordRepository, never()).save(any());
    }

    @Test
    void manualLessonFeedbackReceivesPreviousLessonsOnly() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonRecord previous = new LessonRecord(
            student,
            "关系代词",
            60,
            "复习关系代词",
            "长句拆分仍需提示",
            "完成专项练习",
            LocalDate.of(2026, 7, 20)
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findTop3ByStudentIdOrderByLessonDateDescIdDesc(1L))
            .thenReturn(List.of(previous));
        when(lessonRecordRepository.save(any(LessonRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStructuringClient.generateFeedback(
            any(Student.class),
            any(LessonRecord.class),
            any(),
            any(FeedbackPreferences.class)
        )).thenReturn("本节长句拆分比上次更熟练。");
        when(feedbackRepository.save(any(Feedback.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service().createLesson(1L, request());

        verify(lessonStructuringClient).generateFeedback(
            org.mockito.ArgumentMatchers.eq(student),
            org.mockito.ArgumentMatchers.argThat(record ->
                "定语从句复习".equals(record.getCourseTitle())
            ),
            org.mockito.ArgumentMatchers.eq(List.of(previous)),
            org.mockito.ArgumentMatchers.eq(DEFAULT_PREFERENCES)
        );
    }

    @Test
    void updatesLessonAndRecalculatesStudentLastLessonDate() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        student.recordLessonOn(LocalDate.of(2026, 7, 23));
        LessonRecord edited = new LessonRecord(
            student,
            "原课堂主题",
            60,
            "原课堂内容",
            "原课堂表现",
            "原课后练习",
            LocalDate.of(2026, 7, 23)
        );
        LessonRecord other = new LessonRecord(
            student,
            "关系代词",
            60,
            "复习关系代词",
            "掌握稳定",
            "完成练习",
            LocalDate.of(2026, 7, 20)
        );
        UpdateLessonRequest request = new UpdateLessonRequest(
            "修正后的课堂主题",
            75,
            "修正后的课堂内容",
            "修正后的课堂表现",
            "修正后的课后练习",
            LocalDate.of(2026, 7, 10)
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findByIdAndStudentId(9L, 1L))
            .thenReturn(Optional.of(edited));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(1L))
            .thenReturn(List.of(other, edited));

        LessonResponse response = service().updateLesson(1L, 9L, request);

        assertEquals("修正后的课堂主题", response.courseTitle());
        assertEquals(75, response.durationMinutes());
        assertEquals(LocalDate.of(2026, 7, 10), response.lessonDate());
        assertEquals(LocalDate.of(2026, 7, 20), student.getLastLessonDate());
        verify(lessonStructuringClient, never()).generateFeedback(any(), any(), any(), any());
    }

    @Test
    void keepsUnknownPerformanceAndHomeworkEmptyWhenTeacherEditsLesson() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonRecord edited = new LessonRecord(
            student,
            "原课堂主题",
            60,
            "原课堂内容",
            "原课堂表现",
            "原课后练习",
            LocalDate.of(2026, 7, 23)
        );
        UpdateLessonRequest request = new UpdateLessonRequest(
            "修正后的课堂主题",
            60,
            "修正后的课堂内容",
            "",
            "",
            LocalDate.of(2026, 7, 23)
        );
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findByIdAndStudentId(9L, 1L))
            .thenReturn(Optional.of(edited));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(1L))
            .thenReturn(List.of(edited));

        LessonResponse response = service().updateLesson(1L, 9L, request);

        assertEquals("", response.performance());
        assertEquals("", response.homework());
        verify(lessonStructuringClient, never()).generateFeedback(any(), any(), any(), any());
    }

    private CreateLessonRequest request() {
        return request(null, null, null);
    }

    private CreateLessonRequest request(String feedbackTone, String feedbackLength) {
        return request(feedbackTone, feedbackLength, null);
    }

    private CreateLessonRequest request(
        String feedbackTone,
        String feedbackLength,
        Long analysisId
    ) {
        return new CreateLessonRequest(
            "定语从句复习",
            60,
            "复习关系代词的选择",
            "课堂参与积极，长句分析还需更细心",
            "完成练习册第 12 页",
            LocalDate.of(2026, 7, 23),
            feedbackTone,
            feedbackLength,
            analysisId
        );
    }

    private LessonRecordService service() {
        return new LessonRecordService(
            studentRepository,
            lessonRecordRepository,
            feedbackRepository,
            lessonAnalysisRepository,
            lessonDraftRepository,
            lessonStructuringClient
        );
    }
}

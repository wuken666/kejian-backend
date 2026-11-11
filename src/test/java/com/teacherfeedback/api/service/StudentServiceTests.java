package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.FeedbackRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.StudentResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.SaveLessonDraftRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateStudentRequest;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTests {

    @Test
    void storesFeeAndPreservesOmittedFeeWhileAllowingExplicitClear() {
        Student student = new Student("小雨", "初二", "英语", "");
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        assertEquals(new BigDecimal("150.25"), service().updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "", "", new BigDecimal("150.25"))).lessonFee());
        assertEquals(new BigDecimal("150.25"), service().updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "")).lessonFee());
        assertEquals(null, service().updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "", "", null, true)).lessonFee());
        assertEquals(BigDecimal.ZERO, service().updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "", "", BigDecimal.ZERO)).lessonFee());
    }

    @Test
    void validatesFeePrecisionAndRange() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String invalid : List.of("-1", "1.001", "1000000")) {
                var request = new com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateStudentRequest(
                    "小雨", "初二", "英语", "", "", new BigDecimal(invalid));
                org.junit.jupiter.api.Assertions.assertFalse(validator.validate(request).isEmpty());
            }
            var request = new com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateStudentRequest(
                "小雨", "初二", "英语", "", "", new BigDecimal("999999.99"));
            org.junit.jupiter.api.Assertions.assertTrue(validator.validate(request).isEmpty());
        }
    }

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LessonRecordRepository lessonRecordRepository;
    @Mock
    private LessonDraftRepository lessonDraftRepository;
    @Mock
    private FeedbackRepository feedbackRepository;

    @Test
    void createsStudentWithOptionalTrimmedAddress() {
        when(studentRepository.save(org.mockito.ArgumentMatchers.any(Student.class)))
            .thenAnswer(call -> call.getArgument(0));
        var response = service().createStudent(
            new com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateStudentRequest("小雨", "初二", "英语", "", " 测试小区 1 号 "));
        assertEquals("测试小区 1 号", response.address());
        var withoutAddress = service().createStudent(
            new com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateStudentRequest("小林", "初二", "英语", ""));
        assertEquals("", withoutAddress.address());
    }

    @Test
    void updatesClearsAndPreservesOmittedAddress() {
        Student student = new Student("小雨", "初二", "英语", "");
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        var service = service();
        assertEquals("测试街道", service.updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "", " 测试街道 ")).address());
        assertEquals("测试街道", service.updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "")).address());
        assertEquals("", service.updateStudent(2L,
            new UpdateStudentRequest("小雨", "初二", "英语", "", " ")).address());
    }

    @Test
    void updatesProfileUsedByFutureAiCalls() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(null))
            .thenReturn(List.of());
        when(lessonDraftRepository.findByStudentId(null)).thenReturn(Optional.empty());

        StudentResponse response = service().updateStudent(
            2L,
            new UpdateStudentRequest(
                " 小雨 ",
                " 初三 ",
                " 英语 ",
                " 提升长难句分析和写作表达 "
            )
        );

        assertEquals("小雨", response.name());
        assertEquals("初三", response.grade());
        assertEquals("英语", response.subject());
        assertEquals("提升长难句分析和写作表达", response.goal());
    }

    @Test
    void rejectsMissingStudent() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
            ResponseStatusException.class,
            () -> service().updateStudent(
                99L,
                new UpdateStudentRequest("小雨", "初三", "英语", "学习目标")
            )
        );
    }

    @Test
    void acceptsAnOmittedLearningGoal() {
        Student student = new Student("小雨", "初二", "英语", null);

        assertEquals("", student.getLearningGoal());
    }

    @Test
    void includesUnfinishedDraftSummaryInStudentResponse() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonDraft draft = new LessonDraft(
            student,
            new SaveLessonDraftRequest(
                "定语从句复习",
                60,
                "复习关系代词",
                "",
                "",
                LocalDate.of(2026, 7, 22),
                null
            )
        );
        when(studentRepository.findById(3L)).thenReturn(Optional.of(student));
        when(lessonDraftRepository.findByStudentId(null)).thenReturn(Optional.of(draft));
        when(lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(null))
            .thenReturn(List.of());

        StudentResponse response = service().updateStudent(
            3L,
            new UpdateStudentRequest("小雨", "初二", "英语", "巩固语法")
        );

        assertEquals("定语从句复习", response.draftTitle());
        assertEquals(draft.getUpdatedAt(), response.draftUpdatedAt());
    }

    private StudentService service() {
        return new StudentService(
            studentRepository,
            lessonRecordRepository,
            lessonDraftRepository,
            feedbackRepository
        );
    }

    @Test
    void graduatesAndRestoresWithoutDeletingHistory() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        student.recordLessonOn(LocalDate.of(2026, 11, 5));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        assertEquals(false, student.isGraduated());
        assertEquals(true, service().setGraduated(2L, true).graduated());
        assertEquals(false, service().setGraduated(2L, false).graduated());
        assertEquals(LocalDate.of(2026, 11, 5), student.getLastLessonDate());
        assertEquals("巩固语法", student.getLearningGoal());
        org.mockito.Mockito.verify(studentRepository, org.mockito.Mockito.never()).delete(student);
    }

    @Test
    void rejectsGraduationOfAnotherTeachersStudent() {
        Student student = new Student(1L, "小雨", "初二", "英语", "");
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        com.teacherfeedback.api.auth.TeacherContext.set(99L);
        try {
            assertThrows(ResponseStatusException.class, () -> service().setGraduated(2L, true));
            assertEquals(false, student.isGraduated());
        } finally {
            com.teacherfeedback.api.auth.TeacherContext.clear();
        }
    }
}

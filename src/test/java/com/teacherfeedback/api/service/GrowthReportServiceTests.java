package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.dao.GrowthReportRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.GrowthReportResponse;
import com.teacherfeedback.api.model.GrowthReport;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthReportServiceTests {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LessonRecordRepository lessonRecordRepository;
    @Mock
    private GrowthReportRepository growthReportRepository;
    @Mock
    private LessonStructuringClient lessonStructuringClient;

    @Test
    void generatesAndStoresReportFromChronologicalLessonHistory() {
        Student student = new Student("小雨", "初二", "英语", "提升长句分析能力");
        LessonRecord newest = lesson(student, "定语从句综合", LocalDate.of(2026, 7, 23));
        LessonRecord middle = lesson(student, "关系代词", LocalDate.of(2026, 7, 20));
        LessonRecord oldest = lesson(student, "从句基础", LocalDate.of(2026, 7, 16));
        List<LessonRecord> descending = List.of(newest, middle, oldest);
        List<LessonRecord> chronological = List.of(oldest, middle, newest);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findTop8ByStudentIdOrderByLessonDateDescIdDesc(1L))
            .thenReturn(descending);
        when(lessonStructuringClient.generateGrowthReport(student, chronological))
            .thenReturn("【阶段学习总结】\n长句拆分已经可以独立完成，下一阶段继续巩固关系代词。");
        when(growthReportRepository.save(any(GrowthReport.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GrowthReportResponse response = service().generate(1L);

        assertEquals(3, response.lessonCount());
        assertEquals(LocalDate.of(2026, 7, 16), response.periodStart());
        assertEquals(LocalDate.of(2026, 7, 23), response.periodEnd());
        assertEquals(
            "【阶段学习总结】\n长句拆分已经可以独立完成，下一阶段继续巩固关系代词。",
            response.content()
        );
        verify(lessonStructuringClient).generateGrowthReport(student, chronological);
        verify(growthReportRepository).save(any(GrowthReport.class));
    }

    @Test
    void rejectsGrowthReportUntilTwoLessonsExist() {
        Student student = new Student("小雨", "初二", "英语", "提升长句分析能力");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(lessonRecordRepository.findTop8ByStudentIdOrderByLessonDateDescIdDesc(1L))
            .thenReturn(List.of(lesson(student, "从句基础", LocalDate.of(2026, 7, 16))));

        assertThrows(ResponseStatusException.class, () -> service().generate(1L));

        verify(lessonStructuringClient, never()).generateGrowthReport(any(), any());
        verify(growthReportRepository, never()).save(any());
    }

    private LessonRecord lesson(Student student, String title, LocalDate date) {
        return new LessonRecord(
            student,
            title,
            60,
            title + "知识点",
            "课堂表现有明确记录",
            "完成对应练习",
            date
        );
    }

    private GrowthReportService service() {
        return new GrowthReportService(
            studentRepository,
            lessonRecordRepository,
            growthReportRepository,
            lessonStructuringClient
        );
    }
}

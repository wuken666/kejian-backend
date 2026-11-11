package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.dao.GrowthReportRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.GrowthReportResponse;
import com.teacherfeedback.api.model.GrowthReport;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class GrowthReportService {
    private final StudentRepository studentRepository;
    private final LessonRecordRepository lessonRecordRepository;
    private final GrowthReportRepository growthReportRepository;
    private final LessonStructuringClient lessonStructuringClient;

    public GrowthReportService(
        StudentRepository studentRepository,
        LessonRecordRepository lessonRecordRepository,
        GrowthReportRepository growthReportRepository,
        LessonStructuringClient lessonStructuringClient
    ) {
        this.studentRepository = studentRepository;
        this.lessonRecordRepository = lessonRecordRepository;
        this.growthReportRepository = growthReportRepository;
        this.lessonStructuringClient = lessonStructuringClient;
    }

    @Transactional(readOnly = true)
    public List<GrowthReportResponse> findReports(Long studentId) {
        requireStudent(studentId);
        return growthReportRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public GrowthReportResponse generate(Long studentId) {
        Student student = requireStudent(studentId);
        List<LessonRecord> lessons = new ArrayList<>(
            lessonRecordRepository.findTop8ByStudentIdOrderByLessonDateDescIdDesc(studentId)
        );
        if (lessons.size() < 2) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "至少记录 2 节课堂后才能生成阶段成长总结"
            );
        }

        Collections.reverse(lessons);
        String content = lessonStructuringClient.generateGrowthReport(student, lessons);
        GrowthReport report = growthReportRepository.save(new GrowthReport(
            student,
            lessons.size(),
            lessons.getFirst().getLessonDate(),
            lessons.getLast().getLessonDate(),
            content
        ));
        return toResponse(report);
    }

    @Transactional
    public GrowthReportResponse regenerate(Long studentId, Long reportId) {
        Student student = requireStudent(studentId);
        GrowthReport report = growthReportRepository.findById(reportId)
            .filter(item -> item.getStudent().getId().equals(studentId))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Growth report not found"));
        List<LessonRecord> lessons = latestLessons(studentId);
        String content = lessonStructuringClient.generateGrowthReport(student, lessons);
        report.regenerate(
            lessons.size(),
            lessons.getFirst().getLessonDate(),
            lessons.getLast().getLessonDate(),
            content
        );
        return toResponse(report);
    }

    private List<LessonRecord> latestLessons(Long studentId) {
        List<LessonRecord> lessons = new ArrayList<>(
            lessonRecordRepository.findTop8ByStudentIdOrderByLessonDateDescIdDesc(studentId)
        );
        if (lessons.size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "At least two lessons are required");
        }
        Collections.reverse(lessons);
        return lessons;
    }

    private Student requireStudent(Long studentId) {
        return TeacherContext.requireStudent(studentRepository, studentId);
    }

    private GrowthReportResponse toResponse(GrowthReport report) {
        return new GrowthReportResponse(
            report.getId(),
            report.getStudent().getId(),
            report.getLessonCount(),
            report.getPeriodStart(),
            report.getPeriodEnd(),
            report.getContent(),
            report.getCreatedAt()
        );
    }
}

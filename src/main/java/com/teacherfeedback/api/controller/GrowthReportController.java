package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.GrowthReportResponse;
import com.teacherfeedback.api.service.GrowthReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students/{studentId}/growth-reports")
public class GrowthReportController {
    private final GrowthReportService growthReportService;

    public GrowthReportController(GrowthReportService growthReportService) {
        this.growthReportService = growthReportService;
    }

    @GetMapping
    public List<GrowthReportResponse> findReports(@PathVariable Long studentId) {
        return growthReportService.findReports(studentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthReportResponse generate(@PathVariable Long studentId) {
        return growthReportService.generate(studentId);
    }

    @PostMapping("/{reportId}/regenerate")
    public GrowthReportResponse regenerate(
        @PathVariable Long studentId,
        @PathVariable Long reportId
    ) {
        return growthReportService.regenerate(studentId, reportId);
    }
}

package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.service.TeachingInsightsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class TeachingInsightsController {
    private final TeachingInsightsService insights;

    public TeachingInsightsController(TeachingInsightsService insights) { this.insights = insights; }

    @GetMapping("/api/insights")
    public TeachingInsightsService.Report report(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return insights.report(from, to);
    }
}

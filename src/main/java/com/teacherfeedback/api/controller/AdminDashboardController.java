package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminDashboardResponse;
import com.teacherfeedback.api.service.AdminDashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    // 查询后台总览，可按时间范围和教师筛选。
    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard(
        @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
        @RequestParam(required = false) Long teacherId
    ) {
        return adminDashboardService.loadDashboard(days, teacherId);
    }
}

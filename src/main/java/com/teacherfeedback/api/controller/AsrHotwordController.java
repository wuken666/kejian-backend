package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AsrHotwordResponse;
import com.teacherfeedback.api.service.AsrHotwordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/asr-hotwords")
public class AsrHotwordController {
    private final AsrHotwordService hotwordService;

    public AsrHotwordController(AsrHotwordService hotwordService) {
        this.hotwordService = hotwordService;
    }

    @GetMapping
    public AsrHotwordResponse find(
        @PathVariable Long studentId,
        @RequestParam(defaultValue = "") String courseTitle
    ) {
        return hotwordService.findForStudent(studentId, courseTitle);
    }
}

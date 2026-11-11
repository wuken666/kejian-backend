package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AsrEvaluationResponse;
import com.teacherfeedback.api.service.AsrEvaluationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/asr-evaluations")
public class AsrEvaluationController {
    private final AsrEvaluationService evaluationService;

    public AsrEvaluationController(AsrEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public List<AsrEvaluationResponse> findRecent() {
        return evaluationService.findRecent();
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AsrEvaluationResponse evaluate(
        @RequestParam(required = false) Long studentId,
        @RequestParam(defaultValue = "") @Size(max = 120) String courseTitle,
        @RequestParam @NotBlank @Size(max = 30000) String referenceText,
        @RequestPart("audio") MultipartFile audio
    ) {
        return evaluationService.evaluate(studentId, courseTitle, referenceText, audio);
    }
}

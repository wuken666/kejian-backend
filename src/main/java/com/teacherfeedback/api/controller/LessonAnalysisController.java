package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AnalyzeTextRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonAnalysisResponse;
import com.teacherfeedback.api.service.LessonAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/students/{studentId}/lesson-analyses")
public class LessonAnalysisController {
    private final LessonAnalysisService lessonAnalysisService;

    public LessonAnalysisController(LessonAnalysisService lessonAnalysisService) {
        this.lessonAnalysisService = lessonAnalysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LessonAnalysisResponse analyze(
        @PathVariable Long studentId,
        @RequestPart(value = "image", required = false) MultipartFile image,
        @RequestPart(value = "images", required = false) List<MultipartFile> images,
        @RequestParam(required = false) String feedbackTone,
        @RequestParam(required = false) String feedbackLength
    ) {
        List<MultipartFile> requestedImages = new ArrayList<>();
        if (image != null) requestedImages.add(image);
        if (images != null) requestedImages.addAll(images);
        return lessonAnalysisService.analyze(
            studentId,
            requestedImages,
            feedbackTone,
            feedbackLength
        );
    }

    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LessonAnalysisResponse analyzeText(
        @PathVariable Long studentId,
        @Valid @RequestBody AnalyzeTextRequest request
    ) {
        return lessonAnalysisService.analyzeText(
            studentId,
            request.text(),
            request.feedbackTone(),
            request.feedbackLength(),
            request.sourceType()
        );
    }

    @PostMapping("/{analysisId}/reanalyze")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonAnalysisResponse reanalyze(
        @PathVariable Long studentId,
        @PathVariable Long analysisId,
        @RequestParam(required = false) String feedbackTone,
        @RequestParam(required = false) String feedbackLength
    ) {
        return lessonAnalysisService.reanalyze(
            studentId,
            analysisId,
            feedbackTone,
            feedbackLength
        );
    }

    @PostMapping(value = "/{analysisId}/corrected-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LessonAnalysisResponse correctRecognizedText(
        @PathVariable Long studentId,
        @PathVariable Long analysisId,
        @Valid @RequestBody AnalyzeTextRequest request
    ) {
        return lessonAnalysisService.correctRecognizedText(
            studentId,
            analysisId,
            request.text(),
            request.feedbackTone(),
            request.feedbackLength()
        );
    }
}

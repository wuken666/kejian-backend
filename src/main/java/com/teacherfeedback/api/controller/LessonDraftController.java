package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonDraftResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.SaveLessonDraftRequest;
import com.teacherfeedback.api.service.LessonDraftService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/lesson-draft")
public class LessonDraftController {
    private final LessonDraftService lessonDraftService;

    public LessonDraftController(LessonDraftService lessonDraftService) {
        this.lessonDraftService = lessonDraftService;
    }

    @GetMapping
    public ResponseEntity<LessonDraftResponse> findDraft(@PathVariable Long studentId) {
        return lessonDraftService.findDraft(studentId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public LessonDraftResponse saveDraft(
        @PathVariable Long studentId,
        @Valid @RequestBody SaveLessonDraftRequest request
    ) {
        return lessonDraftService.saveDraft(studentId, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteDraft(@PathVariable Long studentId) {
        lessonDraftService.deleteDraft(studentId);
        return ResponseEntity.noContent().build();
    }
}

package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.service.FeedbackJobService;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/students/{studentId}/feedback-jobs")
public class FeedbackJobController {
    private final FeedbackJobService jobs;
    public FeedbackJobController(FeedbackJobService jobs) { this.jobs = jobs; }
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public FeedbackJobService.Job submit(@PathVariable Long studentId, @Valid @RequestBody CreateLessonRequest request) {
        return jobs.submit(studentId, request);
    }
    @GetMapping
    public List<FeedbackJobService.Job> list(@PathVariable Long studentId) { return jobs.list(studentId); }
    @PostMapping("/{jobId}/retry")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void retry(@PathVariable Long studentId, @PathVariable String jobId) { jobs.retry(studentId, jobId); }
}

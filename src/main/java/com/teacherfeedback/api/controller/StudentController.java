package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateStudentRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.FeedbackResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonCreationResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.RegenerateFeedbackRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.StudentResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.StudentGraduationRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateFeedbackRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateLessonRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateStudentRequest;
import com.teacherfeedback.api.service.FeedbackService;
import com.teacherfeedback.api.service.LessonRecordService;
import com.teacherfeedback.api.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService studentService;
    private final LessonRecordService lessonRecordService;
    private final FeedbackService feedbackService;

    public StudentController(StudentService studentService,
                             LessonRecordService lessonRecordService,
                             FeedbackService feedbackService) {
        this.studentService = studentService;
        this.lessonRecordService = lessonRecordService;
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public List<StudentResponse> findStudents() {
        return studentService.findStudents();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createStudent(@Valid @RequestBody CreateStudentRequest request) {
        return studentService.createStudent(request);
    }

    @PatchMapping("/{studentId}")
    public StudentResponse updateStudent(
        @PathVariable Long studentId,
        @Valid @RequestBody UpdateStudentRequest request
    ) {
        return studentService.updateStudent(studentId, request);
    }

    @PatchMapping("/{studentId}/graduation")
    public StudentResponse setGraduated(
        @PathVariable Long studentId,
        @Valid @RequestBody StudentGraduationRequest request
    ) {
        return studentService.setGraduated(studentId, request.graduated());
    }

    @DeleteMapping("/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudent(@PathVariable Long studentId) {
        studentService.deleteStudent(studentId);
    }

    @GetMapping("/{studentId}/lesson-records")
    public List<LessonResponse> findLessons(@PathVariable Long studentId) {
        return lessonRecordService.findLessons(studentId);
    }

    @PostMapping("/{studentId}/lesson-records")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonCreationResponse createLesson(@PathVariable Long studentId,
                                               @Valid @RequestBody CreateLessonRequest request) {
        return lessonRecordService.createLesson(studentId, request);
    }

    @PatchMapping("/{studentId}/lesson-records/{lessonId}")
    public LessonResponse updateLesson(
        @PathVariable Long studentId,
        @PathVariable Long lessonId,
        @Valid @RequestBody UpdateLessonRequest request
    ) {
        return lessonRecordService.updateLesson(studentId, lessonId, request);
    }

    @GetMapping("/{studentId}/feedbacks")
    public List<FeedbackResponse> findFeedbacks(@PathVariable Long studentId) {
        return feedbackService.findFeedbacks(studentId);
    }

    @PatchMapping("/{studentId}/feedbacks/{feedbackId}")
    public FeedbackResponse updateFeedback(
        @PathVariable Long studentId,
        @PathVariable Long feedbackId,
        @Valid @RequestBody UpdateFeedbackRequest request
    ) {
        return feedbackService.updateFeedback(studentId, feedbackId, request);
    }

    // 老师确认反馈准确后写入服务端，供多设备共享确认状态。
    @PostMapping("/{studentId}/feedbacks/{feedbackId}/confirm")
    public FeedbackResponse confirmFeedback(
        @PathVariable Long studentId,
        @PathVariable Long feedbackId
    ) {
        return feedbackService.confirmFeedback(studentId, feedbackId);
    }

    @PostMapping("/{studentId}/feedbacks/{feedbackId}/regenerate")
    public FeedbackResponse regenerateFeedback(
        @PathVariable Long studentId,
        @PathVariable Long feedbackId,
        @Valid @RequestBody RegenerateFeedbackRequest request
    ) {
        return feedbackService.regenerateFeedback(studentId, feedbackId, request);
    }

    // 删除单条家长反馈，保留对应课堂记录供老师后续重新生成。
    @DeleteMapping("/{studentId}/feedbacks/{feedbackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(
        @PathVariable Long studentId,
        @PathVariable Long feedbackId
    ) {
        feedbackService.deleteFeedback(studentId, feedbackId);
    }
}

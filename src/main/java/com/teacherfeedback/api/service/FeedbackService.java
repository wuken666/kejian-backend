package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.dao.FeedbackRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.FeedbackResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.RegenerateFeedbackRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateFeedbackRequest;
import com.teacherfeedback.api.model.Feedback;
import com.teacherfeedback.api.model.FeedbackGenerationSource;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class FeedbackService {
    private final StudentRepository studentRepository;
    private final FeedbackRepository feedbackRepository;
    private final LessonRecordRepository lessonRecordRepository;
    private final LessonStructuringClient lessonStructuringClient;

    public FeedbackService(StudentRepository studentRepository,
                           FeedbackRepository feedbackRepository,
                           LessonRecordRepository lessonRecordRepository,
                           LessonStructuringClient lessonStructuringClient) {
        this.studentRepository = studentRepository;
        this.feedbackRepository = feedbackRepository;
        this.lessonRecordRepository = lessonRecordRepository;
        this.lessonStructuringClient = lessonStructuringClient;
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findFeedbacks(Long studentId) {
        TeacherContext.requireStudent(studentRepository, studentId);
        return feedbackRepository.findByLessonRecordStudentIdOrderByCreatedAtDesc(studentId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public FeedbackResponse updateFeedback(
        Long studentId,
        Long feedbackId,
        UpdateFeedbackRequest request
    ) {
        Feedback feedback = feedbackRepository
            .findByIdAndLessonRecordStudentId(feedbackId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "反馈记录不存在"));
        TeacherContext.assertStudentAccess(feedback.getLessonRecord().getStudent());
        feedback.revise(request.content());
        return toResponse(feedback);
    }

    // 将老师的核对结果持久化，确保换设备后仍保持已确认状态。
    @Transactional
    public FeedbackResponse confirmFeedback(Long studentId, Long feedbackId) {
        Feedback feedback = feedbackRepository
            .findByIdAndLessonRecordStudentId(feedbackId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "反馈记录不存在"));
        TeacherContext.assertStudentAccess(feedback.getLessonRecord().getStudent());
        feedback.confirm();
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackResponse regenerateFeedback(
        Long studentId,
        Long feedbackId,
        RegenerateFeedbackRequest request
    ) {
        Feedback feedback = feedbackRepository
            .findByIdAndLessonRecordStudentId(feedbackId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "反馈记录不存在"));
        TeacherContext.assertStudentAccess(feedback.getLessonRecord().getStudent());
        LessonRecord lesson = feedback.getLessonRecord();
        Student student = lesson.getStudent();
        FeedbackPreferences preferences = FeedbackPreferences.fromNullable(
            request.feedbackTone(),
            request.feedbackLength()
        );
        List<LessonRecord> history = lessonRecordRepository
            .findByStudentIdOrderByLessonDateDescIdDesc(studentId)
            .stream()
            .filter(record -> !sameLesson(record, lesson))
            .limit(3)
            .toList();

        String regenerated = lessonStructuringClient.generateFeedback(
            student,
            lesson,
            history,
            preferences
        );
        LessonContentQuality.requireGeneratedFeedback(regenerated, lesson.getHomework());
        feedback.replaceGenerated(regenerated, FeedbackGenerationSource.REGENERATED);
        return toResponse(feedback);
    }

    // 删除属于指定学生的单条反馈，不级联删除其课堂记录。
    @Transactional
    public void deleteFeedback(Long studentId, Long feedbackId) {
        Feedback feedback = feedbackRepository
            .findByIdAndLessonRecordStudentId(feedbackId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "反馈记录不存在"));
        TeacherContext.assertStudentAccess(feedback.getLessonRecord().getStudent());
        feedbackRepository.delete(feedback);
    }

    private boolean sameLesson(LessonRecord left, LessonRecord right) {
        if (left == right) return true;
        return left.getId() != null && left.getId().equals(right.getId());
    }

    private FeedbackResponse toResponse(Feedback feedback) {
        LessonRecord lesson = feedback.getLessonRecord();
        return new FeedbackResponse(
            feedback.getId(),
            lesson.getId(),
            lesson.getCourseTitle(),
            feedback.getContent(),
            feedback.getGenerationSource().name(),
            feedback.getCreatedAt(),
            feedback.getRevisedAt(),
            feedback.getConfirmedAt()
        );
    }
}

package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.LessonAnalysisRepository;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonDraftResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonAnalysisResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.SaveLessonDraftRequest;
import com.teacherfeedback.api.model.LessonAnalysis;
import com.teacherfeedback.api.model.LessonDraft;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class LessonDraftService {
    private final StudentRepository studentRepository;
    private final LessonDraftRepository lessonDraftRepository;
    private final LessonAnalysisRepository lessonAnalysisRepository;

    public LessonDraftService(
        StudentRepository studentRepository,
        LessonDraftRepository lessonDraftRepository,
        LessonAnalysisRepository lessonAnalysisRepository
    ) {
        this.studentRepository = studentRepository;
        this.lessonDraftRepository = lessonDraftRepository;
        this.lessonAnalysisRepository = lessonAnalysisRepository;
    }

    @Transactional(readOnly = true)
    public Optional<LessonDraftResponse> findDraft(Long studentId) {
        requireStudent(studentId);
        return lessonDraftRepository.findByStudentId(studentId).map(this::toResponse);
    }

    @Transactional
    public LessonDraftResponse saveDraft(Long studentId, SaveLessonDraftRequest request) {
        Student student = requireStudent(studentId);
        requireAvailableAnalysis(studentId, request.analysisId());
        Optional<LessonDraft> existingDraft = lessonDraftRepository.findByStudentId(studentId);
        LessonDraft draft = existingDraft.orElseGet(() -> new LessonDraft(student, request));
        existingDraft.ifPresent(existing -> existing.update(request));
        return toResponse(lessonDraftRepository.save(draft));
    }

    @Transactional
    public void deleteDraft(Long studentId) {
        requireStudent(studentId);
        lessonDraftRepository.deleteByStudentId(studentId);
    }

    private Student requireStudent(Long studentId) {
        return TeacherContext.requireStudent(studentRepository, studentId);
    }

    private void requireAvailableAnalysis(Long studentId, Long analysisId) {
        if (analysisId == null) return;
        LessonAnalysis analysis = lessonAnalysisRepository.findByIdAndStudentId(analysisId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "截图分析记录不存在"));
        if (analysis.getConfirmedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "这份截图分析已经生成过课堂记录");
        }
    }

    private LessonDraftResponse toResponse(LessonDraft draft) {
        return new LessonDraftResponse(
            draft.getStudent().getId(),
            draft.getCourseTitle(),
            draft.getDurationMinutes(),
            draft.getContent(),
            draft.getPerformance(),
            draft.getHomework(),
            draft.getLessonDate(),
            draft.getAnalysisId(),
            draftAnalysis(draft),
            draft.getUpdatedAt()
        );
    }

    private LessonAnalysisResponse draftAnalysis(LessonDraft draft) {
        if (draft.getAnalysisId() == null) return null;
        return lessonAnalysisRepository
            .findByIdAndStudentId(draft.getAnalysisId(), draft.getStudent().getId())
            .filter(analysis -> analysis.getConfirmedAt() == null)
            .map(this::toAnalysisResponse)
            .orElse(null);
    }

    private LessonAnalysisResponse toAnalysisResponse(LessonAnalysis analysis) {
        return new LessonAnalysisResponse(
            analysis.getId(),
            analysis.getStudent().getId(),
            analysis.getOcrText(),
            analysis.getCourseTitle(),
            analysis.getDurationMinutes(),
            analysis.getContent(),
            analysis.getPerformance(),
            analysis.getHomework(),
            analysis.getFeedbackDraft(),
            analysis.getMissingFields(),
            analysis.getConfidence(),
            analysis.isStudentMatched(),
            analysis.getDetectedStudentNames(),
            analysis.getWarnings(),
            analysis.getCreatedAt(),
            false
        );
    }
}

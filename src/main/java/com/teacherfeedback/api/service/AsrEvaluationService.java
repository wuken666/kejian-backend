package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.client.AsrClient;
import com.teacherfeedback.api.client.AsrClient.TranscriptionResult;
import com.teacherfeedback.api.dao.AsrEvaluationRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AsrEvaluationResponse;
import com.teacherfeedback.api.model.AsrEvaluation;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AsrEvaluationService {
    private final StudentRepository studentRepository;
    private final AsrEvaluationRepository evaluationRepository;
    private final AsrHotwordService hotwordService;
    private final AsrClient asrClient;

    public AsrEvaluationService(
        StudentRepository studentRepository,
        AsrEvaluationRepository evaluationRepository,
        AsrHotwordService hotwordService,
        AsrClient asrClient
    ) {
        this.studentRepository = studentRepository;
        this.evaluationRepository = evaluationRepository;
        this.hotwordService = hotwordService;
        this.asrClient = asrClient;
    }

    @Transactional(readOnly = true)
    public List<AsrEvaluationResponse> findRecent() {
        List<AsrEvaluation> evaluations = TeacherContext.filterTeacherId()
            .map(evaluationRepository::findTop20ByStudentTeacherIdOrderByCreatedAtDesc)
            .orElseGet(evaluationRepository::findTop20ByOrderByCreatedAtDesc);
        return evaluations
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * 识别同一份音频，并将识别结果与人工标准文本逐字对比后保存评测指标。
     */
    @Transactional
    public AsrEvaluationResponse evaluate(
        Long studentId,
        String courseTitle,
        String referenceText,
        MultipartFile audio
    ) {
        Student student = studentId == null ? null : TeacherContext.requireStudent(studentRepository, studentId);
        String normalizedCourseTitle = courseTitle == null ? "" : courseTitle.strip();
        List<String> hotwords = hotwordService.resolveTerms(studentId, normalizedCourseTitle);
        TranscriptionResult transcription = asrClient.transcribe(audio, hotwords);
        CharacterErrorRate.Result metrics = CharacterErrorRate.compare(
            referenceText,
            transcription.text()
        );
        AsrEvaluation evaluation = new AsrEvaluation(
            student,
            audio.getOriginalFilename() == null ? "audio" : audio.getOriginalFilename(),
            normalizedCourseTitle,
            referenceText.strip(),
            transcription.text(),
            metrics,
            transcription.inferenceSeconds(),
            hotwords
        );
        return toResponse(evaluationRepository.save(evaluation));
    }

    private AsrEvaluationResponse toResponse(AsrEvaluation evaluation) {
        Student student = evaluation.getStudent();
        return new AsrEvaluationResponse(
            evaluation.getId(),
            student == null ? null : student.getId(),
            student == null ? null : student.getName(),
            evaluation.getOriginalFilename(),
            evaluation.getCourseTitle(),
            evaluation.getReferenceText(),
            evaluation.getRecognizedText(),
            evaluation.getReferenceCharacters(),
            evaluation.getRecognizedCharacters(),
            evaluation.getSubstitutions(),
            evaluation.getDeletions(),
            evaluation.getInsertions(),
            evaluation.getCharacterErrorRate(),
            evaluation.getInferenceSeconds(),
            evaluation.getHotwords(),
            evaluation.getCreatedAt()
        );
    }
}

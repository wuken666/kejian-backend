package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.FeedbackRepository;
import com.teacherfeedback.api.dao.LessonAnalysisRepository;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.client.LessonStructuringClient;
import com.teacherfeedback.api.client.FeedbackPreferences;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.FeedbackResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonCreationResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateLessonRequest;
import com.teacherfeedback.api.model.Feedback;
import com.teacherfeedback.api.model.FeedbackGenerationSource;
import com.teacherfeedback.api.model.LessonAnalysis;
import com.teacherfeedback.api.model.LessonRecord;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class LessonRecordService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.teacherfeedback.api.dao.ScheduledLessonRepository scheduledLessonRepository;
    private static final DateTimeFormatter CHINESE_DATE = DateTimeFormatter.ofPattern("M 月 d 日");

    private final StudentRepository studentRepository;
    private final LessonRecordRepository lessonRecordRepository;
    private final FeedbackRepository feedbackRepository;
    private final LessonAnalysisRepository lessonAnalysisRepository;
    private final LessonDraftRepository lessonDraftRepository;
    private final LessonStructuringClient lessonStructuringClient;

    public LessonRecordService(StudentRepository studentRepository,
                               LessonRecordRepository lessonRecordRepository,
                               FeedbackRepository feedbackRepository,
                               LessonAnalysisRepository lessonAnalysisRepository,
                               LessonDraftRepository lessonDraftRepository,
                               LessonStructuringClient lessonStructuringClient) {
        this.studentRepository = studentRepository;
        this.lessonRecordRepository = lessonRecordRepository;
        this.feedbackRepository = feedbackRepository;
        this.lessonAnalysisRepository = lessonAnalysisRepository;
        this.lessonDraftRepository = lessonDraftRepository;
        this.lessonStructuringClient = lessonStructuringClient;
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> findLessons(Long studentId) {
        requireStudent(studentId);
        return lessonRecordRepository.findByStudentIdOrderByLessonDateDescIdDesc(studentId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public LessonCreationResponse createLesson(Long studentId, CreateLessonRequest request) {
        Student student = requireStudent(studentId);
        if (request.scheduledLessonId() != null) {
            var scheduled = scheduledLessonRepository.findByIdAndTeacherId(request.scheduledLessonId(), student.getTeacherId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "课程不存在"));
            if (!scheduled.getStudent().getId().equals(studentId)
                || !scheduled.getLessonDate().equals(request.lessonDate())) {
                throw new ResponseStatusException(BAD_REQUEST, "请核对课程学生和日期");
            }
        }
        LessonContentQuality.requireMeaningfulLesson(
            request.courseTitle(),
            request.content(),
            request.performance(),
            request.homework()
        );
        LocalDate lessonDate = request.lessonDate() == null ? LocalDate.now() : request.lessonDate();
        FeedbackPreferences preferences = FeedbackPreferences.fromNullable(
            request.feedbackTone(),
            request.feedbackLength()
        );
        LessonAnalysis analysis = requireAnalysis(studentId, request.analysisId());
        List<LessonRecord> recentLessons = recentLessons(studentId);

        LessonRecord lessonRecord = lessonRecordRepository.save(new LessonRecord(
            student,
            request.courseTitle(),
            request.durationMinutes(),
            request.content(),
            request.performance(),
            request.homework(),
            lessonDate
        ));
        student.recordLessonOn(lessonDate);
        lessonRecord.linkScheduledLesson(request.scheduledLessonId());

        GeneratedFeedback generatedFeedback = analysis == null
            ? generateFeedbackWithFallback(student, lessonRecord, recentLessons, preferences)
            : finalFeedback(
                student,
                lessonRecord,
                analysis,
                request,
                recentLessons,
                preferences
            );
        Feedback feedback = feedbackRepository.save(new Feedback(
            lessonRecord,
            "PARENT",
            generatedFeedback.content(),
            generatedFeedback.source()
        ));
        if (analysis != null) analysis.confirm();
        lessonDraftRepository.deleteByStudentId(studentId);

        return new LessonCreationResponse(toResponse(lessonRecord), toFeedbackResponse(feedback));
    }

    @Transactional
    public LessonResponse updateLesson(
        Long studentId,
        Long lessonId,
        UpdateLessonRequest request
    ) {
        Student student = requireStudent(studentId);
        LessonRecord lesson = lessonRecordRepository.findByIdAndStudentId(lessonId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "课堂记录不存在"));
        lesson.update(
            request.courseTitle(),
            request.durationMinutes(),
            request.content(),
            request.performance(),
            request.homework(),
            request.lessonDate()
        );
        LocalDate latestLessonDate = lessonRecordRepository
            .findByStudentIdOrderByLessonDateDescIdDesc(studentId)
            .stream()
            .map(LessonRecord::getLessonDate)
            .max(LocalDate::compareTo)
            .orElse(null);
        student.refreshLastLessonDate(latestLessonDate);
        return toResponse(lesson);
    }

    private GeneratedFeedback finalFeedback(
        Student student,
        LessonRecord lessonRecord,
        LessonAnalysis analysis,
        CreateLessonRequest request,
        List<LessonRecord> recentLessons,
        FeedbackPreferences preferences
    ) {
        if (analysis.matches(request) && !analysis.getFeedbackDraft().isBlank()) {
            try {
                LessonContentQuality.requireGeneratedFeedback(
                    analysis.getFeedbackDraft(),
                    lessonRecord.getHomework()
                );
                return new GeneratedFeedback(
                    analysis.getFeedbackDraft(),
                    FeedbackGenerationSource.ANALYSIS
                );
            } catch (ResponseStatusException exception) {
                // 分析草稿不符合课堂事实时，继续走安全反馈，不把错误草稿直接发给家长。
            }
        }
        return generateFeedbackWithFallback(student, lessonRecord, recentLessons, preferences);
    }

    private GeneratedFeedback generateFeedbackWithFallback(
        Student student,
        LessonRecord lessonRecord,
        List<LessonRecord> recentLessons,
        FeedbackPreferences preferences
    ) {
        try {
            String generatedFeedback = lessonStructuringClient.generateFeedback(
                student,
                lessonRecord,
                recentLessons,
                preferences
            );
            LessonContentQuality.requireGeneratedFeedback(generatedFeedback, lessonRecord.getHomework());
            return new GeneratedFeedback(
                generatedFeedback,
                FeedbackGenerationSource.AI
            );
        } catch (ResponseStatusException exception) {
            // AI 不可用或违反课堂事实时，使用老师已确认字段生成确定性反馈，保证流程可完成。
            return new GeneratedFeedback(
                composeParentFeedback(student, lessonRecord),
                FeedbackGenerationSource.FALLBACK
            );
        }
    }

    private List<LessonRecord> recentLessons(Long studentId) {
        return lessonRecordRepository.findTop3ByStudentIdOrderByLessonDateDescIdDesc(studentId);
    }

    private LessonAnalysis requireAnalysis(Long studentId, Long analysisId) {
        if (analysisId == null) return null;
        LessonAnalysis analysis = lessonAnalysisRepository.findByIdAndStudentId(analysisId, studentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "截图分析记录不存在"));
        if (analysis.getConfirmedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "这份截图分析已经生成过课堂记录");
        }
        if (!analysis.isStudentMatched()) {
            throw new ResponseStatusException(BAD_REQUEST, "截图内容与当前学生不匹配，请先选择正确学生");
        }
        return analysis;
    }

    private Student requireStudent(Long studentId) {
        return TeacherContext.requireStudent(studentRepository, studentId);
    }

    private LessonResponse toResponse(LessonRecord lesson) {
        java.time.LocalTime startTime = null;
        if (lesson.getScheduledLessonId() != null) {
            startTime = scheduledLessonRepository.findByIdAndTeacherId(
                    lesson.getScheduledLessonId(), lesson.getStudent().getTeacherId())
                .filter(scheduled -> scheduled.getStudent().getId().equals(lesson.getStudent().getId())
                    && scheduled.getLessonDate().equals(lesson.getLessonDate()))
                .map(com.teacherfeedback.api.model.ScheduledLesson::getStartTime)
                .orElse(null);
        }
        return new LessonResponse(
            lesson.getId(),
            lesson.getStudent().getId(),
            lesson.getCourseTitle(),
            lesson.getDurationMinutes(),
            lesson.getContent(),
            lesson.getPerformance(),
            lesson.getHomework(),
            lesson.getLessonDate(),
            lesson.getCreatedAt(),
            startTime
        );
    }

    private FeedbackResponse toFeedbackResponse(Feedback feedback) {
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

    private String composeParentFeedback(Student student, LessonRecord lesson) {
        String learningGoalSection = student.getLearningGoal().isBlank()
            ? ""
            : """

                当前学习目标：
                %s
                """.formatted(student.getLearningGoal());
        String performanceSection = lesson.getPerformance().isBlank()
            ? ""
            : """

                课堂表现：
                %s
                """.formatted(lesson.getPerformance());
        String homeworkSection = lesson.getHomework().isBlank()
            ? ""
            : """

                课后练习：
                %s
                """.formatted(lesson.getHomework());
        return """
            【%s %s课后反馈】

            本节课内容：
            %s%s%s%s

            课堂日期：%s
            """.formatted(
            student.getName(),
            lesson.getCourseTitle(),
            lesson.getContent(),
            performanceSection,
            homeworkSection,
            learningGoalSection,
            lesson.getLessonDate().format(CHINESE_DATE)
        ).strip();
    }

    private record GeneratedFeedback(
        String content,
        FeedbackGenerationSource source
    ) {
    }
}

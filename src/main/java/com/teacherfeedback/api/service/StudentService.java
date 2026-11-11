package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.LessonDraftRepository;
import com.teacherfeedback.api.dao.FeedbackRepository;
import com.teacherfeedback.api.dao.LessonRecordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateStudentRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonSummary;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.StudentResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateStudentRequest;
import com.teacherfeedback.api.model.LessonDraft;
import com.teacherfeedback.api.model.Feedback;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class StudentService {
    private final StudentRepository studentRepository;
    private final LessonRecordRepository lessonRecordRepository;
    private final LessonDraftRepository lessonDraftRepository;
    private final FeedbackRepository feedbackRepository;

    public StudentService(StudentRepository studentRepository,
                          LessonRecordRepository lessonRecordRepository,
                          LessonDraftRepository lessonDraftRepository,
                          FeedbackRepository feedbackRepository) {
        this.studentRepository = studentRepository;
        this.lessonRecordRepository = lessonRecordRepository;
        this.lessonDraftRepository = lessonDraftRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> findStudents() {
        List<Student> students = TeacherContext.filterTeacherId()
            .map(studentRepository::findAllByTeacherId)
            .orElseGet(studentRepository::findAll);
        return students.stream().map(this::toResponse).toList();
    }

    @Transactional
    public StudentResponse createStudent(CreateStudentRequest request) {
        Student student = new Student(
            TeacherContext.currentTeacherId(),
            request.name(), request.grade(), request.subject(), request.learningGoal()
        );
        student.setAddress(request.address());
        student.setLessonFee(request.lessonFee());
        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public StudentResponse updateStudent(Long studentId, UpdateStudentRequest request) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        student.updateProfile(
            request.name(),
            request.grade(),
            request.subject(),
            request.learningGoal()
        );
        if (request.address() != null) student.setAddress(request.address());
        if (Boolean.TRUE.equals(request.clearLessonFee())) student.setLessonFee(null);
        else if (request.lessonFee() != null) student.setLessonFee(request.lessonFee());
        return toResponse(student);
    }

    @Transactional
    public StudentResponse setGraduated(Long studentId, boolean graduated) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        student.setGraduated(graduated);
        return toResponse(student);
    }

    @Transactional
    public void deleteStudent(Long studentId) {
        Student student = TeacherContext.requireStudent(studentRepository, studentId);
        studentRepository.delete(student);
    }

    private StudentResponse toResponse(Student student) {
        LessonDraft draft = lessonDraftRepository.findByStudentId(student.getId()).orElse(null);
        Feedback latestFeedback = feedbackRepository
            .findFirstByLessonRecordStudentIdOrderByCreatedAtDesc(student.getId())
            .orElse(null);
        List<LessonSummary> records = lessonRecordRepository
            .findByStudentIdOrderByLessonDateDescIdDesc(student.getId())
            .stream()
            .map(lesson -> new LessonSummary(
                lesson.getId(),
                lesson.getCourseTitle(),
                lesson.getLessonDate(),
                lesson.getPerformance()
            ))
            .toList();

        return new StudentResponse(
            student.getId(),
            student.getName(),
            student.getGrade(),
            student.getSubject(),
            student.getLearningGoal(),
            student.getStatus(),
            student.getLastLessonDate(),
            student.getUpdatedAt(),
            records,
            draft == null || draft.getCourseTitle().isBlank() ? null : draft.getCourseTitle(),
            draft == null ? null : draft.getUpdatedAt(),
            latestFeedback == null ? null : latestFeedback.getId(),
            latestFeedback == null ? null : latestFeedback.getConfirmedAt(),
            student.getAddress(),
            student.isGraduated(),
            student.getLessonFee()
        );
    }
}

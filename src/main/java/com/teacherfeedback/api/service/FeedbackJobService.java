package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.CreateLessonRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.springframework.http.HttpStatus.*;

@Service
@EnableScheduling
public class FeedbackJobService {
    public record Job(String id, String courseTitle, String status, Long lessonId) {}
    private final JdbcTemplate jdbc;
    private final StudentRepository students;
    private final LessonRecordService lessons;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public FeedbackJobService(JdbcTemplate jdbc, StudentRepository students, LessonRecordService lessons,
                              ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.students = students;
        this.lessons = lessons;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
    }

    public Job submit(Long studentId, CreateLessonRequest request) {
        var student = TeacherContext.requireStudent(students, studentId);
        LessonContentQuality.requireMeaningfulLesson(request.courseTitle(), request.content(), request.performance(), request.homework());
        String json = mapper.writeValueAsString(request);
        String identity = request.scheduledLessonId() != null ? "scheduled:" + request.scheduledLessonId()
            : request.analysisId() != null ? "analysis:" + request.analysisId() : json;
        String key;
        try {
            key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        // The unique key also deduplicates retries after a lost submission response across instances.
        jdbc.update("INSERT INTO feedback_jobs (id,teacher_id,student_id,request_key,request_json,course_title,status) VALUES (?,?,?,?,?,?,'PENDING') ON DUPLICATE KEY UPDATE request_key=request_key",
            UUID.randomUUID().toString(), student.getTeacherId(), studentId, key, json, request.courseTitle());
        return jdbc.queryForObject("SELECT id,course_title,status,lesson_id FROM feedback_jobs WHERE teacher_id=? AND student_id=? AND request_key=?",
            (rs, row) -> new Job(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, Long.class)), student.getTeacherId(), studentId, key);
    }

    public List<Job> list(Long studentId) {
        var student = TeacherContext.requireStudent(students, studentId);
        return jdbc.query("SELECT id,course_title,status,lesson_id FROM feedback_jobs WHERE teacher_id=? AND student_id=? ORDER BY created_at DESC LIMIT 30",
            (rs, row) -> new Job(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, Long.class)), student.getTeacherId(), studentId);
    }

    public void retry(Long studentId, String id) {
        var student = TeacherContext.requireStudent(students, studentId);
        if (jdbc.update("UPDATE feedback_jobs SET status='PENDING',updated_at=CURRENT_TIMESTAMP WHERE id=? AND student_id=? AND teacher_id=? AND status='FAILED'",
            id, studentId, student.getTeacherId()) == 0) throw new ResponseStatusException(CONFLICT, "任务不存在或仍在生成，请刷新后查看");
    }

    @Scheduled(fixedDelay = 2000)
    public void processNext() {
        // A crash rolls back both the lesson and success marker. Stale work becomes visibly retryable.
        jdbc.update("UPDATE feedback_jobs SET status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE status='RUNNING' AND updated_at < TIMESTAMPADD(MINUTE,-10,CURRENT_TIMESTAMP)");
        var ids = jdbc.queryForList("SELECT id FROM feedback_jobs WHERE status='PENDING' ORDER BY created_at LIMIT 1", String.class);
        if (ids.isEmpty()) return;
        String id = ids.getFirst();
        if (jdbc.update("UPDATE feedback_jobs SET status='RUNNING',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", id) != 1) return;
        try {
            transaction.executeWithoutResult(tx -> {
                var row = jdbc.queryForMap("SELECT teacher_id,student_id,request_json FROM feedback_jobs WHERE id=? FOR UPDATE", id);
                TeacherContext.set(((Number) row.get("teacher_id")).longValue());
                var request = mapper.readValue((String) row.get("request_json"), CreateLessonRequest.class);
                if (request.scheduledLessonId() != null) {
                    var existing = jdbc.queryForList("SELECT id FROM lesson_records WHERE student_id=? AND scheduled_lesson_id=? ORDER BY id LIMIT 1",
                        Long.class, row.get("student_id"), request.scheduledLessonId());
                    if (!existing.isEmpty()) {
                        jdbc.update("UPDATE feedback_jobs SET status='SUCCEEDED',lesson_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", existing.getFirst(), id);
                        return;
                    }
                }
                var result = lessons.createLesson(((Number) row.get("student_id")).longValue(), request);
                jdbc.update("UPDATE feedback_jobs SET status='SUCCEEDED',lesson_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", result.lessonRecord().id(), id);
            });
            org.slf4j.LoggerFactory.getLogger(getClass()).info("feedback_job id={} phase=success", id);
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("feedback_job id={} phase=failure error_type={}", id, error.getClass().getSimpleName());
            jdbc.update("UPDATE feedback_jobs SET status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        } finally { TeacherContext.clear(); }
    }
}

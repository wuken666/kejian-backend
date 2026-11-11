package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dao.LessonSessionRepository;
import com.teacherfeedback.api.dao.LessonSessionSegmentRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminDashboardResponse;
import com.teacherfeedback.api.model.LessonSession;
import com.teacherfeedback.api.model.LessonSessionSegment;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "auth.require-teacher-token=false")
@Transactional
class AdminDashboardServiceIntegrationTests {
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private LessonSessionRepository lessonSessionRepository;
    @Autowired
    private LessonSessionSegmentRepository lessonSessionSegmentRepository;
    @Autowired
    private AdminDashboardService adminDashboardService;

    // 验证失真录音会在课堂列表和异常中心显示明确原因，且不暴露课堂正文。
    @Test
    void reportsDistortedAudioAsAnActionableRecordingIncident() {
        Student student = studentRepository.save(new Student("诊断测试", "初二", "英语", ""));
        LessonSession session = lessonSessionRepository.save(new LessonSession(student, "录音诊断课"));
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            "diagnostic-distorted-segment",
            0,
            30,
            ""
        );
        segment.completeTranscription("", "DISTORTED", 0.577, 0.022, 0);
        lessonSessionSegmentRepository.saveAndFlush(segment);

        AdminDashboardResponse dashboard = adminDashboardService.loadDashboard(1, null);
        var sessionRow = dashboard.sessions().stream()
            .filter(item -> item.id() == session.getId())
            .findFirst()
            .orElseThrow();

        assertEquals("DISTORTED_AUDIO", sessionRow.health());
        assertEquals(1, sessionRow.distortedSegmentCount());
        assertTrue(dashboard.incidents().stream().anyMatch(incident ->
            incident.context().equals("lesson_session_distorted_audio")
                && incident.message().contains("1 个片段输入失真")
        ));
    }

    // 验证客户端采集时长明显大于已保存音频时，后台会给出可量化的缺失诊断。
    @Test
    void reportsIncompleteAudioAgainstClientCapturedDuration() {
        Student student = studentRepository.save(new Student("完整性测试", "初二", "英语", ""));
        LessonSession session = new LessonSession(student, "长课堂完整性测试");
        session.reportClientCapturedSeconds(120);
        session.pause();
        session = lessonSessionRepository.save(session);
        Long sessionId = session.getId();
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            "diagnostic-incomplete-segment",
            0,
            30,
            "本节课复习了一般过去时。"
        );
        segment.completeTranscription("本节课复习了一般过去时。", "NORMAL", 0.08, 0, 0.4);
        lessonSessionSegmentRepository.saveAndFlush(segment);

        AdminDashboardResponse dashboard = adminDashboardService.loadDashboard(1, null);
        var sessionRow = dashboard.sessions().stream()
            .filter(item -> item.id() == sessionId)
            .findFirst()
            .orElseThrow();

        assertEquals("INCOMPLETE_AUDIO", sessionRow.health());
        assertEquals(120, sessionRow.clientCapturedSeconds());
        assertEquals(30, sessionRow.recordedSeconds());
        assertEquals(90, sessionRow.missingAudioSeconds());
        assertTrue(dashboard.incidents().stream().anyMatch(incident ->
            incident.context().equals("lesson_session_incomplete_audio")
                && incident.message().contains("疑似缺失 90 秒")
        ));
    }
}

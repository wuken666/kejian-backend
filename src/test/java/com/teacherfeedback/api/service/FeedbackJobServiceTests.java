package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.model.Student;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackJobServiceTests {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    StudentRepository students = mock(StudentRepository.class);
    LessonRecordService lessons = mock(LessonRecordService.class);
    ObjectMapper mapper = mock(ObjectMapper.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    FeedbackJobService service = new FeedbackJobService(jdbc, students, lessons, mapper, manager);
    CreateLessonRequest request = new CreateLessonRequest("英语复习", 60, "复习 need 用法和否定前缀构词法。", "", "", java.time.LocalDate.of(2026,11,18), "professional", "brief", null);
    @AfterEach void cleanup() { TeacherContext.clear(); }

    @Test void submitsWithoutCallingAiAndUsesStableDeduplicationKey() {
        TeacherContext.set(7L);
        when(students.findById(4L)).thenReturn(Optional.of(new Student(7L, "学生", "初三", "英语", "")));
        when(mapper.writeValueAsString(request)).thenReturn("saved-request");
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(4L), anyString()))
            .thenReturn(new FeedbackJobService.Job("job", "英语复习", "PENDING", null));
        assertEquals(service.submit(4L, request), service.submit(4L, request));
        verifyNoInteractions(lessons);
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(4L), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }

    @Test void rejectsOtherTeachersBeforeAcceptingContent() {
        TeacherContext.set(8L);
        when(students.findById(4L)).thenReturn(Optional.of(new Student(7L, "学生", "初三", "英语", "")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.submit(4L, request));
        verifyNoInteractions(jdbc, lessons);
    }

    @Test void onlyClaimWinnerGeneratesAndRestoresTeacherScope() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("job"));
        when(jdbc.update(contains("status='RUNNING',"), eq("job"))).thenReturn(1);
        when(jdbc.queryForMap(anyString(), eq("job"))).thenReturn(Map.of("teacher_id", 7L, "student_id", 4L, "request_json", "saved-request"));
        when(mapper.readValue("saved-request", CreateLessonRequest.class)).thenReturn(request);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var response = mock(LessonCreationResponse.class);
        var record = mock(LessonResponse.class);
        when(response.lessonRecord()).thenReturn(record);
        when(record.id()).thenReturn(12L);
        when(lessons.createLesson(4L, request)).thenAnswer(invocation -> { assertEquals(7L, TeacherContext.currentTeacherId()); return response; });
        service.processNext();
        verify(jdbc).update(contains("status='SUCCEEDED'"), eq(12L), eq("job"));
        assertNull(TeacherContext.currentTeacherId());
        when(jdbc.update(contains("status='RUNNING',"), eq("job"))).thenReturn(0);
        service.processNext();
        verify(lessons, times(1)).createLesson(4L, request);
    }

    @Test void failureRollsBackAndBecomesRetryable() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("job"));
        when(jdbc.update(contains("status='RUNNING',"), eq("job"))).thenReturn(1);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForMap(anyString(), eq("job"))).thenThrow(new IllegalStateException("database unavailable"));
        service.processNext();
        verify(manager).rollback(any());
        verify(jdbc).update(contains("status='FAILED'"), eq("job"));
        assertNull(TeacherContext.currentTeacherId());
    }
}

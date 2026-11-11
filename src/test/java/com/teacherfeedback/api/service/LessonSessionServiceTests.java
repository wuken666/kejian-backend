package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dao.LessonSessionRepository;
import com.teacherfeedback.api.dao.LessonSessionSegmentRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.client.AsrClient;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AppendLessonSessionSegmentRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonSessionResponse;
import com.teacherfeedback.api.model.LessonSession;
import com.teacherfeedback.api.model.LessonSessionSegment;
import com.teacherfeedback.api.model.LessonSessionStatus;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonSessionServiceTests {
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LessonSessionRepository lessonSessionRepository;
    @Mock
    private LessonSessionSegmentRepository lessonSessionSegmentRepository;
    @Mock
    private AsrClient asrClient;
    @Mock
    private AsrHotwordService hotwordService;
    @Mock
    private TemporaryLessonAudioStorage audioStorage;

    @Test
    void restoresTheLatestUnfinishedLessonSession() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        session.pause();
        when(lessonSessionRepository.findFirstByStatusInOrderByUpdatedAtDesc(
            List.of(LessonSessionStatus.RECORDING, LessonSessionStatus.PAUSED)
        )).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of());

        LessonSessionResponse response = service().findActiveSession().orElseThrow();

        assertEquals(18L, response.id());
        assertEquals("PAUSED", response.status());
        assertEquals("小雨", response.studentName());
    }

    @Test
    void refusesToResumeACompletedLessonSession() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        session.complete();
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service().resume(18L)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void preventsASecondPageFromClaimingTheSameRecordingSession() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of());

        service().claimRecording(18L, "owner-a");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service().claimRecording(18L, "owner-b")
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    // 验证迟到的旧心跳不会让客户端录音进度倒退。
    @Test
    void keepsTheLargestClientCapturedDuration() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of());

        LessonSessionResponse latest = service().claimRecording(18L, "owner-a", 125);
        LessonSessionResponse delayed = service().claimRecording(18L, "owner-a", 90);

        assertEquals(125, latest.clientCapturedSeconds());
        assertEquals(125, delayed.clientCapturedSeconds());
    }

    // 验证服务端未收到大部分录音时不能静默完成课堂。
    @Test
    void refusesToCompleteWhenUploadedAudioIsClearlyIncomplete() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            "mobile-uuid-001",
            0,
            30,
            "复习了定语从句"
        );
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of(segment));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service().complete(18L, null, 120, false)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(LessonSessionStatus.RECORDING, session.getStatus());
        verify(audioStorage, never()).deleteSession(18L);
    }

    // 验证老师知情确认后可以使用服务端已经安全保存的内容继续整理。
    @Test
    void allowsTeacherToCompleteWithConfirmedPartialAudio() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            "mobile-uuid-001",
            0,
            30,
            "复习了定语从句"
        );
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of(segment));

        LessonSessionResponse response = service().complete(18L, null, 120, true);

        assertEquals("COMPLETED", response.status());
        assertEquals(120, response.clientCapturedSeconds());
        assertEquals(30, response.capturedSeconds());
        verify(audioStorage).deleteSession(18L);
    }

    @Test
    void ignoresARepeatedClientSegmentDuringRetry() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        LessonSessionSegment savedSegment = new LessonSessionSegment(
            session,
            "mobile-uuid-001",
            0,
            30,
            "复习了定语从句"
        );
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdAndClientSegmentId(18L, "mobile-uuid-001"))
            .thenReturn(Optional.of(savedSegment));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of(savedSegment));

        LessonSessionResponse response = service().appendSegment(
            18L,
            new AppendLessonSessionSegmentRequest("mobile-uuid-001", 0, 30, "复习了定语从句")
        );

        assertEquals(1, response.segments().size());
        assertEquals(30, response.capturedSeconds());
        verify(lessonSessionSegmentRepository, never()).save(any());
    }

    @Test
    void acceptsTheLastAudioSegmentThatArrivesAfterPause() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        session.pause();
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdAndClientSegmentId(18L, "mobile-uuid-002"))
            .thenReturn(Optional.empty());
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of());

        LessonSessionResponse response = service().appendSegment(
            18L,
            new AppendLessonSessionSegmentRequest("mobile-uuid-002", 1, 12, "补充最后一句")
        );

        assertEquals("PAUSED", response.status());
        verify(lessonSessionSegmentRepository).saveAndFlush(any());
    }

    @Test
    void keepsAudioSegmentWhenAsrFails() {
        Student student = new Student("灏忛洦", "鍒濅簩", "鑻辫", "宸╁浐璇硶");
        assignStudentId(student, 7L);
        LessonSession session = new LessonSession(student, "鏆戝亣鑻辫璇?");
        assignId(session, 18L);
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdAndClientSegmentId(18L, "mobile-uuid-003"))
            .thenReturn(Optional.empty());
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of());
        when(hotwordService.resolveTerms(7L, "鏆戝亣鑻辫璇?"))
            .thenReturn(List.of());
        doThrow(new ResponseStatusException(BAD_GATEWAY, "ASR unavailable"))
            .when(asrClient)
            .transcribe(any(MultipartFile.class), any());

        service().transcribeAndAppend(
            18L,
            new com.teacherfeedback.api.dto.TeacherFeedbackDtos.UploadLessonSessionAudioRequest(
                "mobile-uuid-003",
                0,
                30
            ),
            new MockMultipartFile("audio", "segment.webm", "audio/webm", new byte[] { 1, 2, 3 })
        );

        verify(audioStorage).store(eq(18L), eq("mobile-uuid-003"), any(MultipartFile.class));
        ArgumentCaptor<LessonSessionSegment> captor = ArgumentCaptor.forClass(LessonSessionSegment.class);
        verify(lessonSessionSegmentRepository, times(2)).saveAndFlush(captor.capture());
        assertEquals("FAILED", captor.getValue().getAsrStatus());
        assertEquals("UNKNOWN", captor.getValue().getAudioQuality());
    }

    @Test
    void storesAsrStatusAndAudioQualityForEachSegment() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        assignStudentId(student, 7L);
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdAndClientSegmentId(18L, "mobile-uuid-004"))
            .thenReturn(Optional.empty());
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of());
        when(hotwordService.resolveTerms(7L, "暑假英语课")).thenReturn(List.of("定语从句"));
        when(asrClient.transcribe(any(MultipartFile.class), any()))
            .thenReturn(new AsrClient.TranscriptionResult(
                "复习了定语从句",
                0.42,
                "NORMAL",
                0.084,
                0.0002
            ));

        service().transcribeAndAppend(
            18L,
            new com.teacherfeedback.api.dto.TeacherFeedbackDtos.UploadLessonSessionAudioRequest(
                "mobile-uuid-004",
                0,
                30
            ),
            new MockMultipartFile("audio", "segment.pcm", "application/octet-stream", new byte[] { 1, 2 })
        );

        ArgumentCaptor<LessonSessionSegment> captor = ArgumentCaptor.forClass(LessonSessionSegment.class);
        verify(lessonSessionSegmentRepository, times(2)).saveAndFlush(captor.capture());
        LessonSessionSegment saved = captor.getValue();
        assertEquals("TRANSCRIBED", saved.getAsrStatus());
        assertEquals("NORMAL", saved.getAudioQuality());
        assertEquals(0.084, saved.getAudioRms());
        assertEquals(0.0002, saved.getClippingRatio());
        assertEquals(0.42, saved.getAsrInferenceSeconds());
    }

    @Test
    void createsASpeakerSeparatedTranscriptFromStoredLessonAudio() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        assignStudentId(student, 7L);
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            "mobile-uuid-001",
            0,
            30,
            "老师提问，学生回答"
        );
        Path audioPath = Path.of("data/lesson-audio/18/segment.webm");
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of(segment));
        when(audioStorage.find(18L, "mobile-uuid-001")).thenReturn(Optional.of(audioPath));
        when(hotwordService.resolveTerms(7L, "暑假英语课")).thenReturn(List.of("定语从句"));
        when(asrClient.transcribeDialogue(List.of(audioPath), List.of("定语从句")))
            .thenReturn(new AsrClient.DialogueTranscriptionResult(
                "[说话人0] 请回答\n[说话人1] 我选 B",
                List.of(
                    new AsrClient.SpeakerSegment(0, 0, 1000, "请回答"),
                    new AsrClient.SpeakerSegment(1, 1100, 1800, "我选 B")
                ),
                1.2,
                true
            ));

        var response = service().transcribeDialogue(18L);

        assertEquals(2, response.segments().size());
        assertEquals("[说话人0] 请回答\n[说话人1] 我选 B", response.text());
        assertEquals(1, response.segments().get(1).speaker());
        assertEquals(true, response.speakerSeparationReliable());
    }

    @Test
    void fallsBackToSavedTranscriptWhenSpeakerSeparationFails() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        assignStudentId(student, 7L);
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        LessonSessionSegment first = new LessonSessionSegment(
            session,
            "mobile-uuid-001",
            0,
            30,
            "老师讲解了定语从句"
        );
        LessonSessionSegment second = new LessonSessionSegment(
            session,
            "mobile-uuid-002",
            1,
            30,
            "学生完成了选择题"
        );
        Path firstAudio = Path.of("data/lesson-audio/18/first.m4a");
        Path secondAudio = Path.of("data/lesson-audio/18/second.m4a");
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of(first, second));
        when(audioStorage.find(18L, "mobile-uuid-001")).thenReturn(Optional.of(firstAudio));
        when(audioStorage.find(18L, "mobile-uuid-002")).thenReturn(Optional.of(secondAudio));
        when(hotwordService.resolveTerms(7L, "暑假英语课")).thenReturn(List.of("定语从句"));
        when(asrClient.transcribeDialogue(List.of(firstAudio, secondAudio), List.of("定语从句")))
            .thenThrow(new ResponseStatusException(BAD_GATEWAY, "ASR unavailable"));

        var response = service().transcribeDialogue(18L);

        assertEquals("老师讲解了定语从句\n学生完成了选择题", response.text());
        assertEquals(0, response.segments().size());
        assertEquals(false, response.speakerSeparationReliable());
    }

    @Test
    void keepsSavedTranscriptWhenSpeakerSeparationIsNotReliable() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        assignStudentId(student, 7L);
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            "mobile-uuid-001",
            0,
            30,
            "老师讲解了定语从句"
        );
        Path audioPath = Path.of("data/lesson-audio/18/first.m4a");
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(List.of(segment));
        when(audioStorage.find(18L, "mobile-uuid-001")).thenReturn(Optional.of(audioPath));
        when(hotwordService.resolveTerms(7L, "暑假英语课")).thenReturn(List.of("定语从句"));
        when(asrClient.transcribeDialogue(List.of(audioPath), List.of("定语从句")))
            .thenReturn(new AsrClient.DialogueTranscriptionResult(
                "[角色待判断] 无关噪声",
                List.of(new AsrClient.SpeakerSegment(0, 0, 500, "无关噪声")),
                0.8,
                false
            ));

        var response = service().transcribeDialogue(18L);

        assertEquals("老师讲解了定语从句", response.text());
        assertEquals(false, response.speakerSeparationReliable());
    }

    @Test
    void skipsSynchronousSpeakerSeparationForLongLessonsWithSavedTranscript() {
        Student student = new Student("小雨", "初二", "英语", "巩固语法");
        assignStudentId(student, 7L);
        LessonSession session = new LessonSession(student, "暑假英语课");
        assignId(session, 18L);
        List<LessonSessionSegment> segments = IntStream.range(0, 13)
            .mapToObj(index -> new LessonSessionSegment(
                session,
                "mobile-uuid-" + index,
                index,
                30,
                "第 " + index + " 段课堂转写"
            ))
            .toList();
        when(lessonSessionRepository.findById(18L)).thenReturn(Optional.of(session));
        when(lessonSessionSegmentRepository.findByLessonSessionIdOrderBySequenceNumberAsc(18L))
            .thenReturn(segments);
        for (LessonSessionSegment segment : segments) {
            when(audioStorage.find(18L, segment.getClientSegmentId()))
                .thenReturn(Optional.of(Path.of("data/lesson-audio/18/" + segment.getClientSegmentId())));
        }

        var response = service().transcribeDialogue(18L);

        assertEquals(13, response.text().lines().count());
        assertEquals(false, response.speakerSeparationReliable());
        verify(asrClient, never()).transcribeDialogue(any(), any());
        verify(hotwordService, never()).resolveTerms(any(), any());
    }

    @Test
    void deletesSessionSegmentsAndTemporaryAudioWhenRecordingIsDiscarded() {
        when(lessonSessionRepository.existsById(18L)).thenReturn(true);

        service().delete(18L);

        verify(lessonSessionSegmentRepository).deleteByLessonSessionId(18L);
        verify(lessonSessionRepository).deleteById(18L);
        verify(audioStorage).deleteSession(18L);
    }

    @Test
    void treatsRepeatedRecordingDeletionAsSuccessful() {
        when(lessonSessionRepository.existsById(18L)).thenReturn(false);

        service().delete(18L);

        verify(lessonSessionSegmentRepository, never()).deleteByLessonSessionId(18L);
        verify(lessonSessionRepository, never()).deleteById(18L);
        verify(audioStorage).deleteSession(18L);
    }

    private LessonSessionService service() {
        return new LessonSessionService(
            studentRepository,
            lessonSessionRepository,
            lessonSessionSegmentRepository,
            asrClient,
            hotwordService,
            audioStorage
        );
    }

    private void assignId(LessonSession session, long id) {
        try {
            Field field = LessonSession.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void assignStudentId(Student student, long id) {
        try {
            Field field = Student.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

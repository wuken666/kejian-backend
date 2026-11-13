package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.LessonSessionRepository;
import com.teacherfeedback.api.dao.LessonSessionSegmentRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.client.AsrClient;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AppendLessonSessionSegmentRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonSessionResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonSessionSegmentResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonDialogueSegmentResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonDialogueTranscriptResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.StartLessonSessionRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UploadLessonSessionAudioRequest;
import com.teacherfeedback.api.model.LessonSession;
import com.teacherfeedback.api.model.LessonSessionSegment;
import com.teacherfeedback.api.model.LessonSessionStatus;
import com.teacherfeedback.api.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class LessonSessionService {
    private static final Logger log = LoggerFactory.getLogger(LessonSessionService.class);
    private static final int MAX_SYNCHRONOUS_DIALOGUE_SEGMENTS = 12;
    private static final Duration RECORDING_OWNER_LEASE = Duration.ofMinutes(5);
    private static final int AUDIO_COMPLETION_TOLERANCE_SECONDS = 15;
    private static final List<LessonSessionStatus> RESUMABLE_STATUSES = List.of(
        LessonSessionStatus.RECORDING,
        LessonSessionStatus.PAUSED
    );

    private final StudentRepository studentRepository;
    private final LessonSessionRepository lessonSessionRepository;
    private final LessonSessionSegmentRepository lessonSessionSegmentRepository;
    private final AsrClient asrClient;
    private final AsrHotwordService hotwordService;
    private final TemporaryLessonAudioStorage audioStorage;

    public LessonSessionService(
        StudentRepository studentRepository,
        LessonSessionRepository lessonSessionRepository,
        LessonSessionSegmentRepository lessonSessionSegmentRepository,
        AsrClient asrClient,
        AsrHotwordService hotwordService,
        TemporaryLessonAudioStorage audioStorage
    ) {
        this.studentRepository = studentRepository;
        this.lessonSessionRepository = lessonSessionRepository;
        this.lessonSessionSegmentRepository = lessonSessionSegmentRepository;
        this.asrClient = asrClient;
        this.hotwordService = hotwordService;
        this.audioStorage = audioStorage;
    }

    @Transactional(readOnly = true)
    public Optional<LessonSessionResponse> findActiveSession() {
        Optional<LessonSession> activeSession = TeacherContext.filterTeacherId()
            .map(teacherId -> lessonSessionRepository
                .findFirstByStudentTeacherIdAndStatusInOrderByUpdatedAtDesc(teacherId, RESUMABLE_STATUSES))
            .orElseGet(() -> lessonSessionRepository.findFirstByStatusInOrderByUpdatedAtDesc(RESUMABLE_STATUSES));
        return activeSession
            .map(this::toResponse);
    }

    @Transactional
    public LessonSessionResponse start(StartLessonSessionRequest request) {
        Student student = requireStudent(request.studentId());
        LessonSession session = new LessonSession(student, request.courseTitle());
        return toResponse(lessonSessionRepository.save(session));
    }

    // 领取活动课堂的录音写入权，防止第二个页面覆盖或插入同一节课的分片。
    @Transactional
    public LessonSessionResponse claimRecording(Long sessionId, String ownerToken) {
        return claimRecording(sessionId, ownerToken, null);
    }

    // 领取录音写入权时同步客户端实际采集时长，供后台核对录音完整性。
    @Transactional
    public LessonSessionResponse claimRecording(Long sessionId, String ownerToken, Integer capturedSeconds) {
        LessonSession session = requireSession(sessionId);
        ensureRecordingOwner(session, ownerToken);
        session.reportClientCapturedSeconds(capturedSeconds);
        return toResponse(session);
    }

    @Transactional
    public LessonSessionResponse resume(Long sessionId) {
        return resume(sessionId, null);
    }

    // 恢复课堂录音前校验页面占用，避免第二个页面抢占原录音会话。
    @Transactional
    public LessonSessionResponse resume(Long sessionId, String ownerToken) {
        return resume(sessionId, ownerToken, null);
    }

    @Transactional
    public LessonSessionResponse resume(Long sessionId, String ownerToken, Integer capturedSeconds) {
        LessonSession session = requireSession(sessionId);
        // 已完成课堂必须新建会话，防止历史录音片段串入下一节课。
        if (session.getStatus() == LessonSessionStatus.COMPLETED) {
            throw new ResponseStatusException(BAD_REQUEST, "已结束的课堂不能继续录音，请开始一节新课");
        }
        ensureRecordingOwnerIfProvided(session, ownerToken);
        session.reportClientCapturedSeconds(capturedSeconds);
        session.resume();
        return toResponse(session);
    }

    @Transactional
    public LessonSessionResponse pause(Long sessionId) {
        return pause(sessionId, null);
    }

    // 暂停前校验录音占用，避免另一个页面暂停正在进行的课堂。
    @Transactional
    public LessonSessionResponse pause(Long sessionId, String ownerToken) {
        return pause(sessionId, ownerToken, null);
    }

    @Transactional
    public LessonSessionResponse pause(Long sessionId, String ownerToken, Integer capturedSeconds) {
        LessonSession session = requireSession(sessionId);
        ensureRecordingOwnerIfProvided(session, ownerToken);
        session.reportClientCapturedSeconds(capturedSeconds);
        session.pause();
        return toResponse(session);
    }

    @Transactional
    public LessonSessionResponse appendSegment(Long sessionId, AppendLessonSessionSegmentRequest request) {
        LessonSession session = requireSession(sessionId);
        Optional<LessonSessionSegment> existing = lessonSessionSegmentRepository
            .findByLessonSessionIdAndClientSegmentId(sessionId, request.clientSegmentId());
        if (existing.isPresent()) return toResponse(session);
        // 暂停动作与最后一段上传可能同时发生，PAUSED 状态仍应接收已经录下的尾音。
        if (!RESUMABLE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "已结束的课堂不能继续接收录音片段");
        }

        int sequenceNumber = nextAvailableSequenceNumber(sessionId, request.sequenceNumber());
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            request.clientSegmentId(),
            sequenceNumber,
            request.durationSeconds(),
            request.transcript()
        );
        lessonSessionSegmentRepository.saveAndFlush(segment);
        session.touch();
        return toResponse(session);
    }

    @Transactional
    public LessonSessionResponse transcribeAndAppend(
        Long sessionId,
        UploadLessonSessionAudioRequest request,
        MultipartFile audio
    ) {
        return transcribeAndAppend(sessionId, request, audio, null);
    }

    // 带录音页面标识的分片上传入口，成功上传后会自动续租当前课堂。
    @Transactional
    public LessonSessionResponse transcribeAndAppend(
        Long sessionId,
        UploadLessonSessionAudioRequest request,
        MultipartFile audio,
        String ownerToken
    ) {
        LessonSession session = requireSession(sessionId);
        Optional<LessonSessionSegment> existing = lessonSessionSegmentRepository
            .findByLessonSessionIdAndClientSegmentId(sessionId, request.clientSegmentId());
        if (existing.isPresent()) return toResponse(session);
        if (!RESUMABLE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "已结束的课堂不能继续接收录音片段");
        }
        ensureRecordingOwnerIfProvided(session, ownerToken);

        // 分片只暂存到本节课完成，供课后统一进行说话人分离。
        // 先保存音频和空转写，再调用 ASR，避免识别服务异常导致录音整体丢失。
        audioStorage.store(sessionId, request.clientSegmentId(), audio);
        int sequenceNumber = nextAvailableSequenceNumber(sessionId, request.sequenceNumber());
        LessonSessionSegment segment = new LessonSessionSegment(
            session,
            request.clientSegmentId(),
            sequenceNumber,
            request.durationSeconds(),
            ""
        );
        lessonSessionSegmentRepository.saveAndFlush(segment);

        try {
            List<String> hotwords = hotwordService.resolveTerms(
                session.getStudent().getId(),
                session.getCourseTitle()
            );
            AsrClient.TranscriptionResult result = asrClient.transcribe(audio, hotwords);
            segment.completeTranscription(
                result.text(),
                result.audioQuality(),
                result.rms(),
                result.clippingRatio(),
                result.inferenceSeconds()
            );
        } catch (ResponseStatusException exception) {
            segment.markTranscriptionFailed();
            log.warn(
                "ASR failed after audio segment was saved: session_id={} client_segment_id={} status={} reason={}",
                sessionId,
                request.clientSegmentId(),
                exception.getStatusCode().value(),
                exception.getReason()
            );
        } catch (RuntimeException exception) {
            segment.markTranscriptionFailed();
            log.warn(
                "ASR failed after audio segment was saved: session_id={} client_segment_id={} error_type={} message={}",
                sessionId,
                request.clientSegmentId(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
        }

        lessonSessionSegmentRepository.saveAndFlush(segment);
        session.touch();
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public LessonDialogueTranscriptResponse transcribeDialogue(Long sessionId) {
        LessonSession session = requireSession(sessionId);
        List<LessonSessionSegment> segments = lessonSessionSegmentRepository
            .findByLessonSessionIdOrderBySequenceNumberAsc(sessionId);
        String existingTranscript = segments.stream()
            .map(LessonSessionSegment::getTranscript)
            .filter(value -> value != null && !value.isBlank())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        List<Path> audioPaths = segments.stream()
            .map(segment -> audioStorage.find(sessionId, segment.getClientSegmentId()))
            .flatMap(Optional::stream)
            .toList();
        if (audioPaths.isEmpty()) {
            return new LessonDialogueTranscriptResponse(existingTranscript, List.of(), 0, false);
        }
        if (!existingTranscript.isBlank() && audioPaths.size() > MAX_SYNCHRONOUS_DIALOGUE_SEGMENTS) {
            // 长课堂已有逐段转写时直接进入 AI 整理，避免同步重跑全部音频导致手机连接超时。
            log.info(
                "Skipping synchronous speaker separation for lesson session {}: segment_count={}",
                sessionId,
                audioPaths.size()
            );
            return new LessonDialogueTranscriptResponse(existingTranscript, List.of(), 0, false);
        }

        List<String> hotwords = hotwordService.resolveTerms(
            session.getStudent().getId(),
            session.getCourseTitle()
        );
        AsrClient.DialogueTranscriptionResult result;
        try {
            result = asrClient.transcribeDialogue(audioPaths, hotwords);
        } catch (ResponseStatusException exception) {
            // 说话人分离只是增强能力；已有逐段转写时继续整理，不能阻断老师结束课堂。
            if (existingTranscript.isBlank()) throw exception;
            log.warn(
                "Speaker separation failed for lesson session {}, falling back to saved transcript: {}",
                sessionId,
                exception.getReason()
            );
            return new LessonDialogueTranscriptResponse(existingTranscript, List.of(), 0, false);
        }
        List<LessonDialogueSegmentResponse> responseSegments = result.segments().stream()
            .map(segment -> new LessonDialogueSegmentResponse(
                segment.speaker(),
                segment.startMs(),
                segment.endMs(),
                segment.text()
            ))
            .toList();
        // 角色置信度不足时保留逐段识别文本，避免低质量声纹结果覆盖有效课堂内容。
        String transcript = !result.speakerSeparationReliable() && !existingTranscript.isBlank()
            ? existingTranscript
            : result.text();
        return new LessonDialogueTranscriptResponse(
            transcript,
            responseSegments,
            result.inferenceSeconds(),
            result.speakerSeparationReliable()
        );
    }

    @Transactional(readOnly = true)
    public void validateAudioUpload(Long sessionId, String ownerToken) {
        LessonSession session = requireSession(sessionId);
        ensureRecordingOwnerIfProvided(session, ownerToken);
        if (!RESUMABLE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "已结束的课堂不能继续接收录音片段");
        }
    }

    @Transactional
    public LessonSessionResponse complete(Long sessionId) {
        return complete(sessionId, null, null, false);
    }

    // 完成本节课前校验录音占用，只有当前录音页面可以结束课堂。
    @Transactional
    public LessonSessionResponse complete(Long sessionId, String ownerToken) {
        return complete(sessionId, ownerToken, null, false);
    }

    @Transactional
    public LessonSessionResponse complete(Long sessionId, String ownerToken, Integer capturedSeconds) {
        return complete(sessionId, ownerToken, capturedSeconds, false);
    }

    // 默认只允许完整上传的课堂结束；老师明确确认后才可按已保存内容继续整理。
    @Transactional
    public LessonSessionResponse complete(
        Long sessionId,
        String ownerToken,
        Integer capturedSeconds,
        boolean confirmPartialAudio
    ) {
        LessonSession session = requireSession(sessionId);
        // 结束按钮可能因网络重试重复提交，已完成的课堂直接返回，避免阻塞后续 AI 整理。
        if (session.getStatus() == LessonSessionStatus.COMPLETED) {
            session.reportClientCapturedSeconds(capturedSeconds);
            return toResponse(session);
        }
        ensureRecordingOwnerIfProvided(session, ownerToken);
        session.reportClientCapturedSeconds(capturedSeconds);
        List<LessonSessionSegment> segments = lessonSessionSegmentRepository
            .findByLessonSessionIdOrderBySequenceNumberAsc(sessionId);
        if (!confirmPartialAudio && hasIncompleteAudio(session, segments)) {
            throw new ResponseStatusException(
                CONFLICT,
                "录音尚未完整保存，请稍后重试或确认按已保存内容继续"
            );
        }
        session.complete();
        audioStorage.deleteSession(sessionId);
        return toResponse(session);
    }

    // 比较客户端计时、服务端片段时长和序号连续性，识别未上传或中途丢失的录音。
    private boolean hasIncompleteAudio(LessonSession session, List<LessonSessionSegment> segments) {
        int serverCapturedSeconds = segments.stream()
            .mapToInt(LessonSessionSegment::getDurationSeconds)
            .sum();
        int clientCapturedSeconds = Optional.ofNullable(session.getClientCapturedSeconds()).orElse(0);
        if (clientCapturedSeconds - serverCapturedSeconds > AUDIO_COMPLETION_TOLERANCE_SECONDS) {
            return true;
        }
        for (int index = 0; index < segments.size(); index++) {
            if (segments.get(index).getSequenceNumber() != index) return true;
        }
        return false;
    }

    // 放弃本节录音时清理数据库片段、会话和仅用于课堂处理的临时音频。
    @Transactional
    public void delete(Long sessionId) {
        delete(sessionId, null);
    }

    // 删除录音会话时同样校验页面占用，避免其他页面误删正在进行的课堂。
    @Transactional
    public void delete(Long sessionId, String ownerToken) {
        // 移动端可能因弱网重复提交删除；会话已不存在时也按删除成功处理。
        boolean exists = lessonSessionRepository.existsById(sessionId);
        if (!exists) {
            audioStorage.deleteSession(sessionId);
            return;
        }
        LessonSession existingSession = lessonSessionRepository.findById(sessionId).orElse(null);
        if (existingSession != null) {
            TeacherContext.assertStudentAccess(existingSession.getStudent());
        } else if (TeacherContext.hasCurrentTeacher()) {
            throw new ResponseStatusException(NOT_FOUND, "Lesson session not found");
        }
        if (ownerToken != null && !ownerToken.isBlank()) {
            ensureRecordingOwnerIfProvided(requireSession(sessionId), ownerToken);
        }
        lessonSessionSegmentRepository.deleteByLessonSessionId(sessionId);
        lessonSessionRepository.deleteById(sessionId);
        audioStorage.deleteSession(sessionId);
    }

    // 校验录音页面标识，并在每次写入时刷新五分钟占用租约。
    private void ensureRecordingOwnerIfProvided(LessonSession session, String ownerToken) {
        if (ownerToken == null || ownerToken.isBlank()) return;
        ensureRecordingOwner(session, ownerToken);
    }

    // 领取或续租课堂写入权；其他页面只能读取，不能上传、暂停或结束该课堂。
    private void ensureRecordingOwner(LessonSession session, String ownerToken) {
        String normalizedToken = ownerToken == null ? "" : ownerToken.strip();
        if (normalizedToken.isBlank() || normalizedToken.length() > 80) {
            throw new ResponseStatusException(BAD_REQUEST, "无效的录音页面标识");
        }
        if (!RESUMABLE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "已结束的课堂不能继续录音");
        }
        LocalDateTime now = LocalDateTime.now();
        if (
            session.hasActiveRecordingOwner(now, RECORDING_OWNER_LEASE)
            && !session.ownsRecording(normalizedToken)
        ) {
            throw new ResponseStatusException(CONFLICT, "本节课正在其他页面录音，请返回原页面继续");
        }
        if (session.ownsRecording(normalizedToken)) {
            session.heartbeatRecordingOwner(normalizedToken, now);
        } else {
            session.claimRecordingOwner(normalizedToken, now);
        }
    }

    private Student requireStudent(Long studentId) {
        return TeacherContext.requireStudent(studentRepository, studentId);
    }

    private LessonSession requireSession(Long sessionId) {
        LessonSession session = lessonSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lesson session not found"));
        TeacherContext.assertStudentAccess(session.getStudent());
        return session;
    }

    // 客户端离线重试可能携带旧序号，服务端顺延以保证同一节课的分片排序唯一。
    private int nextAvailableSequenceNumber(Long sessionId, int requestedSequenceNumber) {
        List<Integer> assignedSequenceNumbers = lessonSessionSegmentRepository
            .findByLessonSessionIdOrderBySequenceNumberAsc(sessionId)
            .stream()
            .map(LessonSessionSegment::getSequenceNumber)
            .toList();
        int candidate = requestedSequenceNumber;
        while (assignedSequenceNumbers.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private LessonSessionResponse toResponse(LessonSession session) {
        List<LessonSessionSegmentResponse> segments = lessonSessionSegmentRepository
            .findByLessonSessionIdOrderBySequenceNumberAsc(session.getId())
            .stream()
            .map(segment -> new LessonSessionSegmentResponse(
                segment.getId(),
                segment.getClientSegmentId(),
                segment.getSequenceNumber(),
                segment.getDurationSeconds(),
                segment.getTranscript(),
                segment.getAsrStatus(),
                segment.getAudioQuality(),
                segment.getAudioRms(),
                segment.getClippingRatio(),
                segment.getAsrInferenceSeconds(),
                segment.getCreatedAt()
            ))
            .toList();
        int capturedSeconds = segments.stream().mapToInt(LessonSessionSegmentResponse::durationSeconds).sum();
        return new LessonSessionResponse(
            session.getId(),
            session.getStudent().getId(),
            session.getStudent().getName(),
            session.getCourseTitle(),
            session.getStatus().name(),
            session.getStartedAt(),
            session.getResumedAt(),
            session.getUpdatedAt(),
            capturedSeconds,
            session.getClientCapturedSeconds(),
            segments
        );
    }
}

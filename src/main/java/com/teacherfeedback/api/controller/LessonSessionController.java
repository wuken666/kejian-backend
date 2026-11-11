package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AppendLessonSessionSegmentRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonSessionResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonDialogueTranscriptResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.StartLessonSessionRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UploadLessonSessionAudioRequest;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UpdateLessonSessionCaptureRequest;
import com.teacherfeedback.api.service.LessonSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/lesson-sessions")
public class LessonSessionController {
    private final LessonSessionService lessonSessionService;

    public LessonSessionController(LessonSessionService lessonSessionService) {
        this.lessonSessionService = lessonSessionService;
    }

    @GetMapping("/active")
    public ResponseEntity<LessonSessionResponse> findActiveSession() {
        return lessonSessionService.findActiveSession()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public LessonSessionResponse start(@Valid @RequestBody StartLessonSessionRequest request) {
        return lessonSessionService.start(request);
    }

    // 领取本节课堂的录音写入权，防止多个页面同时操作同一节课。
    @PostMapping("/{sessionId}/claim")
    public LessonSessionResponse claim(
        @PathVariable Long sessionId,
        @RequestHeader("X-Recording-Owner") String ownerToken,
        @Valid @RequestBody(required = false) UpdateLessonSessionCaptureRequest request
    ) {
        return lessonSessionService.claimRecording(sessionId, ownerToken, capturedSeconds(request));
    }

    @PostMapping("/{sessionId}/resume")
    public LessonSessionResponse resume(
        @PathVariable Long sessionId,
        @RequestHeader(value = "X-Recording-Owner", required = false) String ownerToken,
        @Valid @RequestBody(required = false) UpdateLessonSessionCaptureRequest request
    ) {
        return lessonSessionService.resume(sessionId, ownerToken, capturedSeconds(request));
    }

    @PostMapping("/{sessionId}/pause")
    public LessonSessionResponse pause(
        @PathVariable Long sessionId,
        @RequestHeader(value = "X-Recording-Owner", required = false) String ownerToken,
        @Valid @RequestBody(required = false) UpdateLessonSessionCaptureRequest request
    ) {
        return lessonSessionService.pause(sessionId, ownerToken, capturedSeconds(request));
    }

    @PostMapping("/{sessionId}/segments")
    public LessonSessionResponse appendSegment(
        @PathVariable Long sessionId,
        @Valid @RequestBody AppendLessonSessionSegmentRequest request
    ) {
        return lessonSessionService.appendSegment(sessionId, request);
    }

    @PostMapping(path = "/{sessionId}/audio-segments", consumes = "multipart/form-data")
    public LessonSessionResponse uploadAudioSegment(
        @PathVariable Long sessionId,
        @Valid UploadLessonSessionAudioRequest request,
        @RequestPart("audio") MultipartFile audio,
        @RequestHeader(value = "X-Recording-Owner", required = false) String ownerToken
    ) {
        return lessonSessionService.transcribeAndAppend(sessionId, request, audio, ownerToken);
    }

    @PostMapping("/{sessionId}/complete")
    public LessonSessionResponse complete(
        @PathVariable Long sessionId,
        @RequestHeader(value = "X-Recording-Owner", required = false) String ownerToken,
        @Valid @RequestBody(required = false) UpdateLessonSessionCaptureRequest request
    ) {
        return lessonSessionService.complete(
            sessionId,
            ownerToken,
            capturedSeconds(request),
            request != null && Boolean.TRUE.equals(request.confirmPartialAudio())
        );
    }

    // 兼容旧客户端未携带请求体的情况，并统一提取客户端录音时长。
    private Integer capturedSeconds(UpdateLessonSessionCaptureRequest request) {
        return request == null ? null : request.capturedSeconds();
    }

    @PostMapping("/{sessionId}/dialogue-transcript")
    public LessonDialogueTranscriptResponse transcribeDialogue(@PathVariable Long sessionId) {
        return lessonSessionService.transcribeDialogue(sessionId);
    }

    // 删除放弃的课堂录音会话，以及服务器保存的转写片段和临时音频。
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long sessionId,
        @RequestHeader(value = "X-Recording-Owner", required = false) String ownerToken
    ) {
        lessonSessionService.delete(sessionId, ownerToken);
    }
}

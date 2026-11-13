package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.client.CloudAudioDownloader;
import com.teacherfeedback.api.service.LessonSessionService;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.LessonSessionResponse;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.UploadLessonSessionAudioRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/lesson-sessions")
public class CloudAudioController {
    private final LessonSessionService sessions;
    private final CloudAudioDownloader downloader;
    public CloudAudioController(LessonSessionService sessions, CloudAudioDownloader downloader) {
        this.sessions = sessions;
        this.downloader = downloader;
    }

    public record CloudAudioRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String clientSegmentId,
        @Min(0) int sequenceNumber,
        @Min(1) @Max(3600) int durationSeconds,
        @NotBlank @Size(max = 8192) String downloadUrl
    ) { }

    @PostMapping("/{sessionId}/cloud-audio-segments")
    public LessonSessionResponse upload(@PathVariable Long sessionId,
        @Valid @RequestBody CloudAudioRequest request,
        @RequestHeader("X-Recording-Owner") String ownerToken) {
        Long teacherId = TeacherContext.currentTeacherId();
        if (teacherId == null) throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        sessions.validateAudioUpload(sessionId, ownerToken);
        String filename = request.clientSegmentId() + ".pcm";
        String path = "recordings/" + teacherId + "/" + sessionId + "/" + filename;
        var audio = downloader.download(request.downloadUrl(), path, filename);
        return sessions.transcribeAndAppend(sessionId, new UploadLessonSessionAudioRequest(
            request.clientSegmentId(), request.sequenceNumber(), request.durationSeconds()), audio, ownerToken);
    }
}

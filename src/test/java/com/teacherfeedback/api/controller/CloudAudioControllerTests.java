package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.client.CloudAudioDownloader;
import com.teacherfeedback.api.service.LessonSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CloudAudioControllerTests {
    @Test
    void downloadsOnlyTheAuthenticatedCoursePathBeforeAppending() {
        var sessions = mock(LessonSessionService.class);
        var downloader = mock(CloudAudioDownloader.class);
        var audio = mock(org.springframework.web.multipart.MultipartFile.class);
        var controller = new CloudAudioController(sessions, downloader);
        var request = new CloudAudioController.CloudAudioRequest("wx-1", 0, 30, "https://private/file");
        when(downloader.download(request.downloadUrl(), "recordings/7/42/wx-1.pcm", "wx-1.pcm")).thenReturn(audio);
        TeacherContext.set(7L);
        try {
            controller.upload(42L, request, "owner");
            var order = inOrder(sessions, downloader);
            order.verify(sessions).validateAudioUpload(42L, "owner");
            order.verify(downloader).download(request.downloadUrl(), "recordings/7/42/wx-1.pcm", "wx-1.pcm");
            order.verify(sessions).transcribeAndAppend(eq(42L), any(), same(audio), eq("owner"));
        } finally { TeacherContext.clear(); }
    }

    @Test
    void checksCourseOwnershipBeforeDownloading() {
        var sessions = mock(LessonSessionService.class);
        var downloader = mock(CloudAudioDownloader.class);
        var controller = new CloudAudioController(sessions, downloader);
        var request = new CloudAudioController.CloudAudioRequest("wx-1", 0, 30, "https://private/file");
        assertThatThrownBy(() -> controller.upload(42L, request, "owner"))
            .isInstanceOf(ResponseStatusException.class);
        TeacherContext.set(7L);
        try {
            doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))
                .when(sessions).validateAudioUpload(42L, "owner");
            assertThatThrownBy(() -> controller.upload(42L, request, "owner"))
                .isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(downloader);
        } finally { TeacherContext.clear(); }
    }
}

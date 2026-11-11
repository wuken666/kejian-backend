package com.teacherfeedback.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryLessonAudioStorageTests {
    @TempDir
    Path tempDirectory;

    // 微信小程序 PCM 分片必须保留原始后缀，供整节课合并时按正确格式解码。
    @Test
    void keepsWechatPcmSuffix() {
        TemporaryLessonAudioStorage storage = new TemporaryLessonAudioStorage(tempDirectory.toString());
        MockMultipartFile audio = new MockMultipartFile(
            "audio",
            "wechat-segment.pcm",
            "application/octet-stream",
            new byte[]{1, 2, 3, 4}
        );

        storage.store(7L, "segment-1", audio);

        Path stored = storage.find(7L, "segment-1").orElseThrow();
        assertThat(stored.getFileName().toString()).endsWith(".pcm");
    }

    @Test
    void deletesAudioAndClosesDirectoryHandleBeforeRemovingFolder() {
        TemporaryLessonAudioStorage storage = new TemporaryLessonAudioStorage(tempDirectory.toString());
        storage.store(7L, "segment-1", new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[]{1, 2}));
        storage.store(7L, "segment-2", new MockMultipartFile("audio", "b.pcm", "audio/pcm", new byte[]{3, 4}));
        storage.deleteSession(7L);
        assertThat(Files.exists(tempDirectory.resolve("7"))).isFalse();
        storage.deleteSession(7L);
        assertThat(storage.find(7L, "segment-1")).isEmpty();
    }
}

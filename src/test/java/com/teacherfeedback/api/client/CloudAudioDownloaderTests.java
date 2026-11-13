package com.teacherfeedback.api.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class CloudAudioDownloaderTests {
    @Test
    void allowsOnlyConfiguredHttpsHostAndOwnedPath() {
        var downloader = new CloudAudioDownloader("bucket.cos.ap-shanghai.myqcloud.com");
        String path = "recordings/7/42/wx-42-1.pcm";
        String base = "https://bucket.cos.ap-shanghai.myqcloud.com/";
        assertThat(downloader.validateUrl(base + path + "?sign=private", path).getPath()).isEqualTo("/" + path);
        for (String url : new String[] {
            "http://bucket.cos.ap-shanghai.myqcloud.com/" + path,
            "https://127.0.0.1/" + path,
            "https://bucket.cos.ap-shanghai.myqcloud.com.evil.com/" + path,
            base + "recordings/8/42/wx-42-1.pcm",
            "https://user@bucket.cos.ap-shanghai.myqcloud.com/" + path,
            "https://bucket.cos.ap-shanghai.myqcloud.com:8090/" + path,
            base + path + "#fragment"
        }) {
            assertThatThrownBy(() -> downloader.validateUrl(url, path)).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> new CloudAudioDownloader("").validateUrl(base + path, path))
            .isInstanceOf(ResponseStatusException.class);
    }
}

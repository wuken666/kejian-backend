package com.teacherfeedback.api.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.*;
import java.nio.file.Files;
import static org.springframework.http.HttpStatus.*;

@Component
public class CloudAudioDownloader {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final String allowedHost;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public CloudAudioDownloader(@Value("${cloud.storage.download-host:}") String allowedHost) {
        this.allowedHost = allowedHost.strip().toLowerCase(java.util.Locale.ROOT);
    }

    URI validateUrl(String url, String expectedPath) {
        if (allowedHost.isEmpty()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "请配置云存储下载域名");
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !allowedHost.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !("/" + expectedPath).equals(uri.getPath())) throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(BAD_REQUEST, "录音云文件地址无效");
        }
    }

    public MultipartFile download(String url, String path, String filename) {
        URI uri = validateUrl(url, path);
        try {
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) throw new IOException("Storage response unavailable");
                byte[] data = input.readNBytes(MAX_BYTES + 1);
                if (data.length == 0 || data.length > MAX_BYTES) throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "录音文件为空或过大");
                return new AudioFile(filename, data);
            }
        } catch (IOException error) {
            // Signed URLs contain credentials; never put the URL or exception message into logs.
            throw new ResponseStatusException(BAD_GATEWAY, "读取云端录音失败，请重试");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "读取云端录音中断，请重试");
        }
    }

    private record AudioFile(String filename, byte[] data) implements MultipartFile {
        public String getName() { return "audio"; }
        public String getOriginalFilename() { return filename; }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { return data.length == 0; }
        public long getSize() { return data.length; }
        public byte[] getBytes() { return data; }
        public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        public void transferTo(File file) throws IOException { Files.write(file.toPath(), data); }
    }
}

package com.teacherfeedback.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Component
public class AsrClient {
    private static final Logger log = LoggerFactory.getLogger(AsrClient.class);
    private final HttpClient httpClient;
    private final URI healthUri;
    private final URI transcribeUri;
    private final URI transcribeDialogueUri;
    private final Duration timeout;
    private final Duration dialogueTimeout;
    private final ObjectMapper objectMapper;

    public AsrClient(Environment environment) {
        String baseUrl = environment.getProperty("services.asr.base-url", "http://127.0.0.1:8090");
        int timeoutSeconds = environment.getProperty("services.asr.timeout-seconds", Integer.class, 45);
        int dialogueTimeoutSeconds = environment.getProperty(
            "services.asr.dialogue-timeout-seconds",
            Integer.class,
            300
        );
        // Uvicorn 本地服务仅使用 HTTP/1.1，避免 JDK 客户端尝试 HTTP/2 升级而破坏 multipart 请求。
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.healthUri = URI.create(baseUrl + "/health");
        this.transcribeUri = URI.create(baseUrl + "/transcribe");
        this.transcribeDialogueUri = URI.create(baseUrl + "/transcribe-dialogue");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.dialogueTimeout = Duration.ofSeconds(dialogueTimeoutSeconds);
        this.objectMapper = new ObjectMapper();
    }

    // 检查 FunASR 模型和流式识别是否就绪；失败时只返回不可用状态，避免健康页暴露内部地址。
    public HealthStatus checkHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("external_call provider=funasr operation=health phase=failure status={}", response.statusCode());
                return HealthStatus.unavailable();
            }
            HealthResponse payload = objectMapper.readValue(response.body(), HealthResponse.class);
            return new HealthStatus(
                true,
                "ready".equalsIgnoreCase(payload.status()),
                Boolean.TRUE.equals(payload.streaming())
            );
        } catch (IOException exception) {
            log.warn(
                "external_call provider=funasr operation=health phase=failure error_type={} message={}",
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
            return HealthStatus.unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return HealthStatus.unavailable();
        } catch (RuntimeException exception) {
            log.warn(
                "external_call provider=funasr operation=health phase=failure error_type={} message={}",
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
            return HealthStatus.unavailable();
        }
    }

    public String transcribe(MultipartFile audio) {
        return transcribe(audio, List.of()).text();
    }

    public TranscriptionResult transcribe(MultipartFile audio, List<String> hotwords) {
        try {
            String boundary = "----TeacherFeedback" + UUID.randomUUID().toString().replace("-", "");
            // 使用 JDK 原生 HTTP 客户端上传分片，避开 Spring HTTP 客户端的版本冲突。
            log.info(
                "external_call provider=funasr operation=transcribe transport=jdk_http phase=start hotword_count={}",
                hotwords.size()
            );
            HttpRequest request = HttpRequest.newBuilder(transcribeUri)
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, audio, hotwords)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422) {
                throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "Audio segment is unreadable");
            }
            if (response.statusCode() / 100 != 2) {
                log.warn(
                    "external_call provider=funasr operation=transcribe phase=failure status={} response={}",
                    response.statusCode(),
                    response.body()
                );
                throw new ResponseStatusException(BAD_GATEWAY, "ASR service rejected the audio");
            }
            AsrResponse payload = objectMapper.readValue(response.body(), AsrResponse.class);
            return new TranscriptionResult(
                payload.text() == null ? "" : payload.text(),
                payload.inferenceSeconds() == null ? 0 : payload.inferenceSeconds(),
                payload.audioQuality() == null ? "UNKNOWN" : payload.audioQuality(),
                payload.rms() == null ? 0 : payload.rms(),
                payload.clippingRatio() == null ? 0 : payload.clippingRatio()
            );
        } catch (IOException exception) {
            log.warn(
                "external_call provider=funasr operation=transcribe phase=failure error_type={} message={}",
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
            throw new ResponseStatusException(BAD_GATEWAY, "Unable to read audio segment", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "ASR request interrupted", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                "external_call provider=funasr operation=transcribe phase=failure error_type={} message={}",
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
            throw new ResponseStatusException(BAD_GATEWAY, "ASR service is unavailable", exception);
        }
    }

    // 课后一次性上传本节课的全部分片，获取带说话人编号的高精度转写。
    public DialogueTranscriptionResult transcribeDialogue(
        List<Path> audioPaths,
        List<String> hotwords
    ) {
        if (audioPaths.isEmpty()) {
            return new DialogueTranscriptionResult("", List.of(), 0, false);
        }
        try {
            String boundary = "----TeacherFeedback" + UUID.randomUUID().toString().replace("-", "");
            log.info(
                "external_call provider=funasr operation=transcribe_dialogue phase=start segment_count={} "
                    + "hotword_count={}",
                audioPaths.size(),
                hotwords.size()
            );
            HttpRequest request = HttpRequest.newBuilder(transcribeDialogueUri)
                .timeout(dialogueTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                    dialogueMultipartBody(boundary, audioPaths, hotwords)
                ))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                    "external_call provider=funasr operation=transcribe_dialogue phase=failure "
                        + "status={} response={}",
                    response.statusCode(),
                    response.body()
                );
                throw new ResponseStatusException(BAD_GATEWAY, "课堂说话人识别失败，请稍后重试");
            }
            DialogueAsrResponse payload = objectMapper.readValue(
                response.body(),
                DialogueAsrResponse.class
            );
            List<SpeakerSegment> segments = payload.segments() == null
                ? List.of()
                : payload.segments();
            return new DialogueTranscriptionResult(
                payload.text() == null ? "" : payload.text(),
                segments,
                payload.inferenceSeconds() == null ? 0 : payload.inferenceSeconds(),
                Boolean.TRUE.equals(payload.speakerSeparationReliable())
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "课堂音频读取失败，请重试", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "课堂说话人识别已中断", exception);
        }
    }

    // 使用 JDK 原生 multipart，避免 Spring HTTP 客户端版本冲突影响本地 ASR 调用。
    private byte[] multipartBody(
        String boundary,
        MultipartFile audio,
        List<String> hotwords
    ) throws IOException {
        String filename = audio.getOriginalFilename() == null ? "segment.webm" : audio.getOriginalFilename();
        String contentType = audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Content-Disposition: form-data; name=\"hotwords\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(objectMapper.writeValueAsBytes(hotwords));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"audio\"; filename=\"" + filename + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(audio.getBytes());
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    // 构造重复 audio 字段，FunASR 会按数据库分片顺序拼接整节课。
    private byte[] dialogueMultipartBody(
        String boundary,
        List<Path> audioPaths,
        List<String> hotwords
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Content-Disposition: form-data; name=\"hotwords\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(objectMapper.writeValueAsBytes(hotwords));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        for (Path audioPath : audioPaths) {
            String filename = audioPath.getFileName().toString();
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(
                ("Content-Disposition: form-data; name=\"audio\"; filename=\"" + filename + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            output.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            Files.copy(audioPath, output);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HealthResponse(
        String status,
        Boolean streaming
    ) {
    }

    public record HealthStatus(
        boolean reachable,
        boolean modelReady,
        boolean streamingReady
    ) {
        public static HealthStatus unavailable() {
            return new HealthStatus(false, false, false);
        }

        public boolean available() {
            return reachable && modelReady && streamingReady;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AsrResponse(
        String text,
        @JsonProperty("inference_seconds") Double inferenceSeconds,
        @JsonProperty("audio_quality") String audioQuality,
        Double rms,
        @JsonProperty("clipping_ratio") Double clippingRatio
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DialogueAsrResponse(
        String text,
        List<SpeakerSegment> segments,
        @JsonProperty("inference_seconds") Double inferenceSeconds,
        @JsonProperty("speaker_separation_reliable") Boolean speakerSeparationReliable
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeakerSegment(
        int speaker,
        @JsonProperty("start_ms") int startMs,
        @JsonProperty("end_ms") int endMs,
        String text
    ) {
    }

    public record TranscriptionResult(
        String text,
        double inferenceSeconds,
        String audioQuality,
        double rms,
        double clippingRatio
    ) {
        // 保留原有测试和调用方的简化构造方式，未提供的诊断数据记为未知。
        public TranscriptionResult(String text, double inferenceSeconds) {
            this(text, inferenceSeconds, "UNKNOWN", 0, 0);
        }
    }

    public record DialogueTranscriptionResult(
        String text,
        List<SpeakerSegment> segments,
        double inferenceSeconds,
        boolean speakerSeparationReliable
    ) {
    }
}

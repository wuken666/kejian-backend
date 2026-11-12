package com.teacherfeedback.api.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AsrClientFailureTests {
    @TempDir
    Path directory;

    @Test
    void distinguishesMissingLocalAudio() {
        var client = new AsrClient(new MockEnvironment());
        var error = assertThrows(ResponseStatusException.class,
            () -> client.transcribeDialogue(List.of(directory.resolve("missing.wav")), List.of()));
        assertEquals("课堂音频读取失败，请重试", error.getReason());
    }

    @Test
    void distinguishesInvalidServiceResponseWithoutLoggingItsContent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/transcribe-dialogue", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "private-response-not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        Logger logger = (Logger) LoggerFactory.getLogger(AsrClient.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var client = new AsrClient(new MockEnvironment()
                .withProperty("services.asr.base-url", "http://127.0.0.1:" + server.getAddress().getPort()));
            Path audio = Files.write(directory.resolve("private-audio.wav"), new byte[]{1});
            var error = assertThrows(ResponseStatusException.class,
                () -> client.transcribeDialogue(List.of(audio), List.of("private-hotword")));
            assertEquals("语音识别服务返回内容异常，请稍后重试", error.getReason());
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(logs.contains("stage=parse_response"));
            assertFalse(logs.contains("private-response"));
            assertFalse(logs.contains("private-audio"));
            assertFalse(logs.contains("private-hotword"));
        } finally {
            logger.detachAppender(appender);
            server.stop(0);
        }
    }

    @Test
    void distinguishesConnectionFailureFromMissingAudio() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.start();
        server.stop(0);
        var client = new AsrClient(new MockEnvironment()
            .withProperty("services.asr.base-url", "http://127.0.0.1:" + port)
            .withProperty("services.asr.dialogue-timeout-seconds", "2"));
        Path audio = Files.write(directory.resolve("test.wav"), new byte[]{1});
        var error = assertThrows(ResponseStatusException.class,
            () -> client.transcribeDialogue(List.of(audio), List.of()));
        assertEquals("语音识别服务连接或响应异常，录音已保留，请稍后重试", error.getReason());
    }
}

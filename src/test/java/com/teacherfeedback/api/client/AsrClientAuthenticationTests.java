package com.teacherfeedback.api.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsrClientAuthenticationTests {
    @TempDir
    Path tempDirectory;

    @Test
    void sendsSharedSecretForBothTranscriptionEndpoints() throws Exception {
        verifyRequests("test-only-asr-token", "Bearer test-only-asr-token");
    }

    @Test
    void preservesLocalRequestsWithoutToken() throws Exception {
        verifyRequests("", "");
    }

    private void verifyRequests(String token, String expectedAuthorization) throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            received.add(exchange.getRequestURI().getPath() + ":" + (authorization == null ? "" : authorization));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"status\":\"ready\",\"streaming\":true,\"text\":\"test\",\"segments\":[]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var environment = new MockEnvironment()
                .withProperty("services.asr.base-url", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("services.asr.service-token", token);
            var client = new AsrClient(environment);
            assertEquals(true, client.checkHealth().available());
            assertEquals("test", client.transcribe(new MockMultipartFile("audio", "test.wav", "audio/wav", new byte[]{1}), List.of()).text());
            Path audio = Files.write(tempDirectory.resolve("test.wav"), new byte[]{1});
            assertEquals("test", client.transcribeDialogue(List.of(audio), List.of()).text());
            assertEquals(List.of("/health:", "/transcribe:" + expectedAuthorization,
                "/transcribe-dialogue:" + expectedAuthorization), received);
        } finally {
            server.stop(0);
        }
    }
}

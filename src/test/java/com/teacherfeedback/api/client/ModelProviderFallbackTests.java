package com.teacherfeedback.api.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelProviderFallbackTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> requestedPaths = new ArrayList<>();
    private final List<String> requestedModels = new ArrayList<>();
    private HttpServer server;
    private int primaryStatus = 200;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    // CloudBase 的 provider 路径必须完整保留，不能被 Chat Completions 子路径覆盖。
    @Test
    void preservesCloudBaseGatewayPath() throws Exception {
        DeepSeekLessonStructuringClient client = startClient("");

        client.structure(student(), "本节复习了一般过去时，学生能够完成基础练习。", List.of(), preferences());

        assertEquals(List.of("/v1/ai/cloudbase/chat/completions"), requestedPaths);
        assertEquals(List.of("primary-model"), requestedModels);
    }

    // 主模型被限流时允许切换到同一提供方下的兜底模型。
    @Test
    void fallsBackAfterRateLimit() throws Exception {
        primaryStatus = 429;
        DeepSeekLessonStructuringClient client = startClient("fallback-model");

        client.structure(student(), "本节复习了一般过去时，学生能够完成基础练习。", List.of(), preferences());

        assertEquals(List.of("primary-model", "fallback-model"), requestedModels);
    }

    // 鉴权失败属于配置问题，不能切换模型掩盖错误或重复产生费用。
    @Test
    void doesNotFallbackAfterAuthenticationFailure() throws Exception {
        primaryStatus = 401;
        DeepSeekLessonStructuringClient client = startClient("fallback-model");

        assertThrows(
            ResponseStatusException.class,
            () -> client.structure(
                student(),
                "本节复习了一般过去时，学生能够完成基础练习。",
                List.of(),
                preferences()
            )
        );

        assertEquals(List.of("primary-model"), requestedModels);
    }

    // 启动本地 OpenAI 兼容假网关，记录真实请求路径与模型选择。
    private DeepSeekLessonStructuringClient startClient(String fallbackModel) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleModelRequest);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/ai/cloudbase";
        return new DeepSeekLessonStructuringClient(
            objectMapper,
            "cloudbase",
            baseUrl,
            "test-key",
            "primary-model",
            fallbackModel
        );
    }

    // 按模型返回成功、限流或鉴权响应，模拟真实提供方故障边界。
    @SuppressWarnings("unchecked")
    private void handleModelRequest(HttpExchange exchange) throws IOException {
        requestedPaths.add(exchange.getRequestURI().getPath());
        Map<String, Object> request = objectMapper.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
        String model = String.valueOf(request.get("model"));
        requestedModels.add(model);
        int status = "primary-model".equals(model) ? primaryStatus : 200;
        byte[] body = status == 200 ? successBody() : errorBody(status);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    // 返回可被现有结构化解析器读取的最小合法课堂结果。
    private byte[] successBody() throws IOException {
        String content = objectMapper.writeValueAsString(Map.ofEntries(
            Map.entry("courseTitle", "英语课堂"),
            Map.entry("durationMinutes", 60),
            Map.entry("content", "复习一般过去时"),
            Map.entry("performance", "能够完成基础练习"),
            Map.entry("homework", ""),
            Map.entry("feedbackDraft", "本节复习了一般过去时，课堂练习完成稳定。"),
            Map.entry("missingFields", List.of("homework")),
            Map.entry("confidence", 0.9),
            Map.entry("studentMatched", true),
            Map.entry("detectedStudentNames", List.of()),
            Map.entry("warnings", List.of())
        ));
        Map<String, Object> response = Map.of(
            "choices", List.of(Map.of("message", Map.of("content", content)))
        );
        return objectMapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
    }

    // 返回标准 JSON 错误体，便于 RestClient 按 HTTP 状态处理。
    private byte[] errorBody(int status) {
        return ("{\"error\":{\"message\":\"test " + status + "\"}}")
            .getBytes(StandardCharsets.UTF_8);
    }

    private Student student() {
        return new Student("测试学生", "初二", "英语", "巩固语法");
    }

    private FeedbackPreferences preferences() {
        return FeedbackPreferences.fromNullable(null, null);
    }
}

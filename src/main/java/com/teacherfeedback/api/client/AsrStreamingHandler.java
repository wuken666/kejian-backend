package com.teacherfeedback.api.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class AsrStreamingHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AsrStreamingHandler.class);
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    private final HttpClient client;
    private final URI baseUri;
    private final String serviceToken;
    private final ConcurrentHashMap<String, WebSocket> upstreams = new ConcurrentHashMap<>();

    @Autowired
    public AsrStreamingHandler(Environment environment) {
        this(environment, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    AsrStreamingHandler(Environment environment, HttpClient client) {
        this.client = client;
        URI base = URI.create(environment.getProperty("services.asr.base-url", "http://127.0.0.1:8090"));
        baseUri = URI.create(("https".equals(base.getScheme()) ? "wss" : "ws")
            + "://" + base.getRawAuthority() + base.getRawPath().replaceAll("/$", "") + "/ws/transcribe");
        serviceToken = environment.getProperty("services.asr.service-token", "").strip();
    }

    URI targetUri(URI incoming) {
        var target = UriComponentsBuilder.fromUri(baseUri);
        if (incoming != null) {
            var terms = UriComponentsBuilder.fromUri(incoming).build().getQueryParams().get("hotword");
            if (terms != null) terms.stream().limit(100).forEach(term -> target.queryParam("hotword", term));
        }
        // Never forward user credentials or arbitrary upstream addresses from the query string.
        return target.build(true).toUri();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setBinaryMessageSizeLimit(MAX_MESSAGE_BYTES);
        session.setTextMessageSizeLimit(8192);
        var downstream = new ConcurrentWebSocketSessionDecorator(session, 10_000, MAX_MESSAGE_BYTES);
        var builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (!serviceToken.isBlank()) builder.header("Authorization", "Bearer " + serviceToken);
        try {
            var upstream = builder.buildAsync(targetUri(session.getUri()), new WebSocket.Listener() {
                private final StringBuilder text = new StringBuilder();

                @Override
                public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                    try {
                        if (text.length() + data.length() > MAX_MESSAGE_BYTES) {
                            socket.abort();
                            close(downstream, CloseStatus.TOO_BIG_TO_PROCESS);
                            return null;
                        }
                        text.append(data);
                        if (last) {
                            downstream.sendMessage(new TextMessage(text.toString()));
                            text.setLength(0);
                        }
                        socket.request(1);
                    } catch (IOException error) {
                        socket.abort();
                        close(downstream, CloseStatus.SERVER_ERROR);
                    }
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
                    upstreams.remove(session.getId(), socket);
                    close(downstream, status == 1000 ? CloseStatus.NORMAL : CloseStatus.SERVER_ERROR);
                    return null;
                }

                @Override
                public void onError(WebSocket socket, Throwable error) {
                    upstreams.remove(session.getId(), socket);
                    log.warn("streaming_asr phase=upstream_failure error_type={}", error.getClass().getSimpleName());
                    close(downstream, CloseStatus.SERVER_ERROR);
                }
            }).get(7, TimeUnit.SECONDS);
            upstreams.put(session.getId(), upstream);
            if (!session.isOpen() || upstream.isInputClosed()) {
                upstreams.remove(session.getId(), upstream);
                upstream.abort();
            }
        } catch (Exception error) {
            log.warn("streaming_asr phase=connect_failure error_type={}", error.getClass().getSimpleName());
            close(downstream, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        WebSocket socket = upstreams.get(session.getId());
        if (socket == null) { close(session, CloseStatus.SERVER_ERROR); return; }
        socket.sendBinary(message.getPayload(), true).get(10, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocket socket = upstreams.get(session.getId());
        if (socket == null) { close(session, CloseStatus.SERVER_ERROR); return; }
        socket.sendText(message.getPayload(), true).get(10, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocket socket = upstreams.remove(session.getId());
        if (socket != null) socket.abort();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
        close(session, CloseStatus.SERVER_ERROR);
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try { if (session.isOpen()) session.close(status); }
        catch (IOException ignored) { }
    }
}

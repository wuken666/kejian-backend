package com.teacherfeedback.api.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsrStreamingHandlerTests {
    @Test
    void relaysAudioAndTranscriptUsingOnlyServerCredentials() throws Exception {
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class, RETURNS_SELF);
        WebSocket upstream = mock(WebSocket.class);
        WebSocketSession downstream = mock(WebSocketSession.class);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(upstream));
        when(downstream.getId()).thenReturn("session-1");
        when(downstream.isOpen()).thenReturn(true);
        when(downstream.getUri()).thenReturn(URI.create("wss://api.example/asr-stream?hotword=%E8%8B%B1%E8%AF%AD&access=private&url=evil"));
        when(upstream.sendBinary(any(), eq(true))).thenReturn(CompletableFuture.completedFuture(upstream));
        when(upstream.sendText(any(), eq(true))).thenReturn(CompletableFuture.completedFuture(upstream));
        var env = new MockEnvironment().withProperty("services.asr.base-url", "http://asr.internal:8090")
            .withProperty("services.asr.service-token", "server-secret");
        var handler = new AsrStreamingHandler(env, client);

        handler.afterConnectionEstablished(downstream);
        var target = ArgumentCaptor.forClass(URI.class);
        var listener = ArgumentCaptor.forClass(WebSocket.Listener.class);
        verify(builder).buildAsync(target.capture(), listener.capture());
        assertThat(target.getValue().toString()).isEqualTo("ws://asr.internal:8090/ws/transcribe?hotword=%E8%8B%B1%E8%AF%AD");
        verify(builder).header("Authorization", "Bearer server-secret");

        ByteBuffer audio = ByteBuffer.allocateDirect(4);
        audio.put(new byte[] {1, 2, 3, 4}).flip();
        handler.handleMessage(downstream, new BinaryMessage(audio));
        verify(upstream).sendBinary(audio, true);
        handler.handleMessage(downstream, new TextMessage("{\"event\":\"commit\"}"));
        verify(upstream).sendText("{\"event\":\"commit\"}", true);

        listener.getValue().onText(upstream, "{\"type\":", false);
        listener.getValue().onText(upstream, "\"partial\",\"text\":\"hello\"}", true);
        var reply = ArgumentCaptor.forClass(TextMessage.class);
        verify(downstream).sendMessage(reply.capture());
        assertThat(reply.getValue().getPayload()).isEqualTo("{\"type\":\"partial\",\"text\":\"hello\"}");
        handler.afterConnectionClosed(downstream, CloseStatus.NORMAL);
        verify(upstream).abort();
    }

    @Test
    void closesClientWhenAsrConnectionFails() throws Exception {
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class, RETURNS_SELF);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("unavailable")));
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        new AsrStreamingHandler(new MockEnvironment(), client).afterConnectionEstablished(session);
        verify(session).close(CloseStatus.SERVER_ERROR);
    }
}

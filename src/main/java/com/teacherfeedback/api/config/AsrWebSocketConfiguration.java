package com.teacherfeedback.api.config;

import com.teacherfeedback.api.client.AsrStreamingHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AsrWebSocketConfiguration implements WebSocketConfigurer {
    private final AsrStreamingHandler handler;

    public AsrWebSocketConfiguration(AsrStreamingHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Native miniapp origins vary; the existing bearer-token filter still authenticates the handshake.
        registry.addHandler(handler, "/asr-stream").setAllowedOriginPatterns("*");
    }
}

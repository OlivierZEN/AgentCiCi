package com.codehouse.ciciassistant.ai.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AsrWebSocketConfig implements WebSocketConfigurer {

    private final AliyunRealtimeAsrWebSocketHandler handler;

    public AsrWebSocketConfig(AliyunRealtimeAsrWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/asr").setAllowedOrigins("*");
    }
}


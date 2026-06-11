package com.teamflow.ai.modules.notification.service;

import com.teamflow.ai.common.security.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;
    private final CorsProperties corsProperties;

    public NotificationWebSocketConfig(NotificationWebSocketHandler handler, CorsProperties corsProperties) {
        this.handler = handler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 与 HTTP 层 CORS 共用同一份白名单（teamflow.cors.allowed-origins），
        // 握手阶段精确校验 Origin，防止跨站 WebSocket 劫持（CSWSH）。
        String[] allowedOrigins = corsProperties.getAllowedOrigins().toArray(new String[0]);
        registry.addHandler(handler, "/ws/notifications")
                .setAllowedOrigins(allowedOrigins);
    }
}

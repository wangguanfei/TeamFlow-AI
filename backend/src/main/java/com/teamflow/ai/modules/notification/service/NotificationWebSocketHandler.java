package com.teamflow.ai.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.security.JwtClaims;
import com.teamflow.ai.common.security.JwtService;
import com.teamflow.ai.common.security.JwtTokenType;
import com.teamflow.ai.modules.notification.dto.NotificationItem;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final String TARGET_USER = "USER";
    private static final String TARGET_ALL = "ALL";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setTextMessageSizeLimit(4096);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (session.getAttributes().containsKey("userId")) {
            return;
        }
        Long userId = authenticate(readAuthToken(message.getPayload()));
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
        session.getAttributes().put("userId", userId);
        sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long id) {
            Set<WebSocketSession> userSessions = sessions.get(id);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(id);
                }
            }
        }
    }

    public void push(NotificationItem item) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "NOTIFICATION_CREATED",
                    "data", item
            ));
            if (TARGET_ALL.equalsIgnoreCase(item.targetType())) {
                sessions.values().forEach(userSessions -> userSessions.forEach(session -> send(session, payload)));
                return;
            }
            if (TARGET_USER.equalsIgnoreCase(item.targetType()) && item.targetId() != null) {
                sessions.getOrDefault(item.targetId(), Set.of()).forEach(session -> send(session, payload));
            }
        } catch (Exception ignored) {
            // WebSocket push is best-effort; persisted notifications remain the source of truth.
        }
    }

    private void send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException ignored) {
            // Drop failed realtime pushes; clients refresh unread state through HTTP.
        }
    }

    private String readAuthToken(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> message = objectMapper.readValue(payload, Map.class);
            if (!"AUTH".equals(message.get("type"))) {
                return null;
            }
            Object token = message.get("token");
            return token instanceof String value ? value : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private Long authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            JwtClaims claims = jwtService.parse(token);
            if (claims.tokenType() != JwtTokenType.ACCESS || claims.expiresAt().isBefore(Instant.now())) {
                return null;
            }
            return claims.userId();
        } catch (Exception exception) {
            return null;
        }
    }
}

package com.teamflow.ai.modules.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingActionStore {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "teamflow:agent:pending:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, StoredPendingAction> fallbackStore = new ConcurrentHashMap<>();

    public PendingActionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String save(PendingAction action) {
        String token = "agt_" + UUID.randomUUID().toString().replace("-", "");
        StoredPendingAction stored = new StoredPendingAction(action, Instant.now().plus(TTL).toEpochMilli());
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + token, objectMapper.writeValueAsString(stored), TTL);
        } catch (Exception e) {
            fallbackStore.put(token, stored);
        }
        return token;
    }

    public PendingAction consume(String token, Long userId) {
        StoredPendingAction stored = load(token);
        if (stored == null || stored.expiresAtEpochMs() < Instant.now().toEpochMilli()) {
            fallbackStore.remove(token);
            throw new BusinessException("确认已过期，请重新发起操作");
        }
        PendingAction action = stored.action();
        if (!action.userId().equals(userId)) {
            throw new BusinessException(403, "不能确认他人的 AI 操作");
        }
        delete(token);
        return action;
    }

    public PendingAction cancel(String token, Long userId) {
        StoredPendingAction stored = load(token);
        if (stored == null) {
            return null;
        }
        if (!stored.action().userId().equals(userId)) {
            throw new BusinessException(403, "不能取消他人的 AI 操作");
        }
        delete(token);
        return stored.action();
    }

    public String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return token;
        }
    }

    private StoredPendingAction load(String token) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + token);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, StoredPendingAction.class);
            }
        } catch (Exception ignored) {
            // Redis 不可用时走本地兜底。
        }
        return fallbackStore.get(token);
    }

    private void delete(String token) {
        try {
            redisTemplate.delete(KEY_PREFIX + token);
        } catch (Exception ignored) {
        }
        fallbackStore.remove(token);
    }

    public record PendingAction(
            Long actionId,
            Long userId,
            Long sessionId,
            String toolName,
            Map<String, Object> arguments,
            Map<String, Object> preview,
            String requiredPermission
    ) {
    }

    public record StoredPendingAction(PendingAction action, long expiresAtEpochMs) {
    }
}

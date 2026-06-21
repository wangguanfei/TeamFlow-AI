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

/**
 * Agent 写操作确认令牌存储。
 *
 * <p>写工具在模型返回后不会立即执行，而是把参数、预览和权限要求保存成短期 pending action。
 * 前端拿到 confirmToken 后展示确认卡片；用户确认时再消费 token。Redis 是首选存储，
 * 本地 ConcurrentHashMap 只是 Redis 不可用时的单实例兜底。</p>
 */
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
            // 兜底只在当前 JVM 有效，容器重启或多实例切换后 token 会失效；这是可接受的安全失败。
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
            // 审计表只保存 hash，避免 confirmToken 明文进入数据库。
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

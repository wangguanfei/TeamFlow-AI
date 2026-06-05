package com.teamflow.ai.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * JWT 黑名单，支持主动登出使 access token 立即失效。
 *
 * <p>无状态 JWT 在签发后无法撤销，仅能等待自然过期。登出 / 强制下线场景需要一个
 * 服务端撤销机制：登出时将该 token 写入 Redis 黑名单，TTL 设为 token 剩余有效期，
 * 过期后随 Redis 自动清理（不会无限堆积）。{@link JwtAuthenticationFilter} 在每次
 * 鉴权时校验黑名单。
 *
 * <p>以 token 的 SHA-256 摘要作为 key，避免在 Redis 中明文存储 token；使用
 * {@link StringRedisTemplate} 保持纯字符串语义。所有 Redis 操作均降级处理：
 * 写入失败仅记日志；查询失败时<strong>放行</strong>（fail-open），优先保证 Redis
 * 故障时不影响正常用户鉴权——这与项目其余缓存的降级策略一致。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将 token 加入黑名单，TTL 为其距离过期的剩余时间。
     * 已过期或剩余时间非正则无需写入（token 本就会被 JWT 解析拒绝）。
     */
    public void blacklist(String token, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(keyOf(token), "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("写入 JWT 黑名单失败", ex);
        }
    }

    /** 校验 token 是否已被登出。Redis 故障时放行（返回 false）。 */
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(keyOf(token)));
        } catch (RuntimeException ex) {
            log.warn("读取 JWT 黑名单失败，放行", ex);
            return false;
        }
    }

    private String keyOf(String token) {
        return KEY_PREFIX + sha256Hex(token);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 是 JDK 标准算法，理论上不会发生
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}

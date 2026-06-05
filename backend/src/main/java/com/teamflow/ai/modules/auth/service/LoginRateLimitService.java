package com.teamflow.ai.modules.auth.service;

import com.teamflow.ai.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败限流，防止暴力撞库。
 *
 * <p>按 username 与 IP 双维度统计失败次数（Redis {@code INCR} + {@code EXPIRE}）：
 * username 维度针对单账号爆破，IP 维度针对同源多账号扫描。任一维度超过阈值即锁定，
 * 锁定窗口随 TTL 自动解除；登录成功清除该账号计数。
 *
 * <p>使用 {@link StringRedisTemplate} 以保证 {@code INCR} 操作的数值语义不受 JSON
 * 序列化器影响。所有 Redis 操作均降级处理：缓存故障时放行（不限流），避免因缓存
 * 不可用导致全站无法登录。
 */
@Service
public class LoginRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitService.class);

    private static final String USER_KEY_PREFIX = "login:fail:user:";
    private static final String IP_KEY_PREFIX = "login:fail:ip:";

    /** 单账号失败阈值。 */
    private static final int USER_MAX_FAILURES = 5;
    /** 单 IP 失败阈值（一个 IP 可能有多名正常用户，阈值放宽）。 */
    private static final int IP_MAX_FAILURES = 20;
    /** 锁定 / 计数窗口。 */
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 登录前校验是否已被锁定，超过阈值抛出业务异常。 */
    public void checkNotLocked(String username, String ip) {
        if (isLocked(USER_KEY_PREFIX + username, USER_MAX_FAILURES)
                || isLocked(IP_KEY_PREFIX + ip, IP_MAX_FAILURES)) {
            throw new BusinessException(429, "登录失败次数过多，请 " + LOCK_WINDOW.toMinutes() + " 分钟后再试");
        }
    }

    /** 记录一次登录失败（username 与 IP 维度各 +1）。 */
    public void recordFailure(String username, String ip) {
        increment(USER_KEY_PREFIX + username);
        increment(IP_KEY_PREFIX + ip);
    }

    /** 登录成功后清除该账号的失败计数。 */
    public void clearFailures(String username) {
        try {
            redisTemplate.delete(USER_KEY_PREFIX + username);
        } catch (RuntimeException ex) {
            log.warn("清除登录失败计数失败 username={}", username, ex);
        }
    }

    private boolean isLocked(String key, int max) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null && Long.parseLong(value) >= max;
        } catch (RuntimeException ex) {
            log.warn("读取登录限流计数失败，放行 key={}", key, ex);
            return false;
        }
    }

    private void increment(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // 仅在首次计数时设置过期窗口，避免每次失败都刷新 TTL（否则攻击者可无限延长窗口）
            if (count != null && count == 1L) {
                redisTemplate.expire(key, LOCK_WINDOW);
            }
        } catch (RuntimeException ex) {
            log.warn("写入登录限流计数失败 key={}", key, ex);
        }
    }
}

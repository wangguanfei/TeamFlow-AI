package com.teamflow.ai.modules.auth.service;

import com.teamflow.ai.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
    static final int USER_MAX_FAILURES = 5;
    /** 单 IP 失败阈值（一个 IP 可能有多名正常用户，阈值放宽）。 */
    private static final int IP_MAX_FAILURES = 20;
    /** 锁定 / 计数窗口。 */
    static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 登录前校验是否已被锁定，超过阈值抛出业务异常（含精确剩余时间）。 */
    public void checkNotLocked(String username, String ip) {
        boolean userLocked = isLocked(USER_KEY_PREFIX + username, USER_MAX_FAILURES);
        boolean ipLocked = isLocked(IP_KEY_PREFIX + ip, IP_MAX_FAILURES);
        if (userLocked || ipLocked) {
            long remainSeconds = getRemainSeconds(USER_KEY_PREFIX + username);
            throw new BusinessException(429, "登录失败次数过多，请" + formatRemaining(remainSeconds) + "后再试");
        }
    }

    /** 记录一次登录失败，返回 username 维度的当前失败计数。 */
    public int recordFailure(String username, String ip) {
        int userCount = increment(USER_KEY_PREFIX + username);
        increment(IP_KEY_PREFIX + ip);
        return userCount;
    }

    /**
     * 根据当前失败次数构建登录错误消息。
     * 第 3、4 次失败时提示剩余尝试次数；达到阈值时提示账号已锁定。
     */
    public String buildLoginErrorMsg(int failCount) {
        int remaining = USER_MAX_FAILURES - failCount;
        if (remaining <= 0) {
            return "账号或密码错误，账号已被锁定，请" + LOCK_WINDOW.toMinutes() + "分钟后再试";
        }
        if (remaining <= 2) {
            return "账号或密码错误，还可尝试" + remaining + "次，" +
                   "累计失败" + USER_MAX_FAILURES + "次后账号将被锁定" + LOCK_WINDOW.toMinutes() + "分钟";
        }
        return "账号或密码错误";
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

    private long getRemainSeconds(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (RuntimeException ex) {
            log.warn("读取登录锁定剩余时间失败 key={}", key, ex);
            return 0;
        }
    }

    private String formatRemaining(long seconds) {
        if (seconds <= 0) {
            return LOCK_WINDOW.toMinutes() + " 分钟";
        }
        if (seconds < 60) {
            return seconds + " 秒";
        }
        long minutes = (seconds + 59) / 60;
        return minutes + " 分钟";
    }

    private int increment(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // 仅在首次计数时设置过期窗口，避免每次失败都刷新 TTL（否则攻击者可无限延长窗口）
            if (count != null && count == 1L) {
                redisTemplate.expire(key, LOCK_WINDOW);
            }
            return count == null ? 0 : count.intValue();
        } catch (RuntimeException ex) {
            log.warn("写入登录限流计数失败 key={}", key, ex);
            return 0;
        }
    }
}

package com.teamflow.ai.modules.ai.service;

import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.modules.ai.provider.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 演示账号 AI 调用限额。
 *
 * <p>公开演示账号不能无限消耗真实模型额度。这里按 Asia/Shanghai 自然日用 Redis INCR 计数，
 * Redis 异常时选择 fail-closed：拒绝本次调用，而不是放开限额。</p>
 */
@Service
public class DemoAiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(DemoAiQuotaService.class);

    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String KEY_PREFIX = "ai:demo:quota:";

    private final RedisQuotaCounter quotaCounter;
    private final AiProperties properties;

    @Autowired
    public DemoAiQuotaService(StringRedisTemplate redisTemplate, AiProperties properties) {
        this(new StringRedisQuotaCounter(redisTemplate), properties);
    }

    DemoAiQuotaService(RedisQuotaCounter quotaCounter, AiProperties properties) {
        this.quotaCounter = quotaCounter;
        this.properties = properties;
    }

    /**
     * 演示账号发起 AI 调用时必须调用此方法消耗当日配额。
     * 达到 demoDailyLimit 上限时抛 BusinessException(429)；Redis 故障时也拒绝（fail-closed）。
     * 配额基于 Asia/Shanghai 自然日，每日零点自动重置（Redis key TTL 精确到下一日 00:00）。
     */
    public void consumeForDemo() {
        int limit = properties.getDemoDailyLimit();
        if (limit <= 0) {
            throw quotaExceeded(limit);
        }
        String key = quotaKey();
        try {
            long count = quotaCounter.increment(key);
            if (count == 1L) {
                // 第一次调用当天 key 时设置到明天 00:00 的 TTL，避免旧计数长期残留。
                boolean expires = quotaCounter.expire(key, ttlToNextDay());
                if (!expires) {
                    throw new IllegalStateException("Redis EXPIRE failed");
                }
            }
            if (count > limit) {
                throw quotaExceeded(limit);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Redis 计数失败时拒绝调用，避免限额系统故障导致演示账号无限调用真实模型。
            log.warn("演示账号 AI 限额计数失败，已拒绝本次调用 key={}", key, exception);
            throw new BusinessException(429, "演示账号AI限额服务暂不可用，请稍后再试");
        }
    }

    private BusinessException quotaExceeded(int limit) {
        return new BusinessException(429, "演示账号今日 AI 调用次数已达 " + limit + " 次，请明天再试");
    }

    private String quotaKey() {
        String today = LocalDate.now(QUOTA_ZONE).format(DATE_FORMATTER);
        return KEY_PREFIX + DemoAccountConstants.USERNAME + ":" + today;
    }

    private Duration ttlToNextDay() {
        ZonedDateTime now = ZonedDateTime.now(QUOTA_ZONE);
        ZonedDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(QUOTA_ZONE);
        long seconds = Math.max(60, Duration.between(now, tomorrow).getSeconds());
        return Duration.ofSeconds(seconds);
    }

    interface RedisQuotaCounter {
        /** 抽象出来便于单元测试模拟 Redis 正常、超限和故障三种场景。 */
        long increment(String key);

        boolean expire(String key, Duration ttl);
    }

    private static class StringRedisQuotaCounter implements RedisQuotaCounter {
        private final StringRedisTemplate redisTemplate;

        private StringRedisQuotaCounter(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public long increment(String key) {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                throw new IllegalStateException("Redis INCR returned null");
            }
            return count;
        }

        @Override
        public boolean expire(String key, Duration ttl) {
            return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
        }
    }
}

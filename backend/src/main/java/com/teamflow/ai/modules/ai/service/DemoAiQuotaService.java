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

    public void consumeForDemo() {
        int limit = properties.getDemoDailyLimit();
        if (limit <= 0) {
            throw quotaExceeded(limit);
        }
        String key = quotaKey();
        try {
            long count = quotaCounter.increment(key);
            if (count == 1L) {
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

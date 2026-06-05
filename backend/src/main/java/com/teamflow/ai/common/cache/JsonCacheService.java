package com.teamflow.ai.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 通用 JSON 缓存原语，供各业务缓存复用（工作台统计、菜单树、RAG 检索等）。
 *
 * <p>刻意不复用全局 {@code RedisTemplate}：其 {@code GenericJackson2JsonRedisSerializer} 既未注册
 * JavaTime 模块（无法处理 {@code LocalDateTime}），又会写入 {@code @class} 类型元数据，跨类型/泛型还原
 * 脆弱。这里改用 {@link StringRedisTemplate} + 自带 {@link ObjectMapper}（注册 JavaTimeModule、关闭
 * 时间戳数字化）存普通 JSON，并通过 {@link TypeReference} 精确还原泛型。
 *
 * <p>所有 Redis 操作均做降级：读写故障时回退 loader 查库，保证业务不因缓存不可用而失效。
 */
@Service
public class JsonCacheService {

    private static final Logger log = LoggerFactory.getLogger(JsonCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public JsonCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    /**
     * 读取缓存，未命中时通过 loader 查库并回填。
     *
     * @param key    缓存键
     * @param ttl    过期时间（兜底失效）
     * @param type   反序列化目标类型（保留泛型信息）
     * @param loader 缓存未命中时的数据加载逻辑
     */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, type);
            }
        } catch (Exception ex) {
            log.warn("读取缓存失败，回退数据库 key={}", key, ex);
        }
        T value = loader.get();
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception ex) {
            log.warn("写入缓存失败 key={}", key, ex);
        }
        return value;
    }

    /** 删除单个缓存键，失败仅告警不抛出（缓存陈旧靠 TTL 兜底）。 */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("删除缓存失败 key={}", key, ex);
        }
    }

    /** 按前缀批量删除（用于一次写入可能影响任意键的场景，如文档变更使所有 RAG 检索结果失效）。 */
    public void evictByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("按前缀删除缓存失败 prefix={}", prefix, ex);
        }
    }
}

package com.teamflow.ai.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 用户角色 / 权限编码缓存。
 *
 * <p>{@code JwtAuthenticationFilter} 在每个请求上都会查询用户的角色与权限，
 * 缓存命中后可消除这部分数据库压力。所有缓存读写都做了降级处理：Redis 不可用时
 * 回退到数据库 loader，保证认证链路不因缓存故障而中断。
 *
 * <p>缓存为最终一致：除写时主动失效外，还设置了 TTL 作为兜底，避免失效点遗漏导致
 * 长期脏数据。
 */
@Service
public class PermissionCacheService {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheService.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final String ROLE_KEY_PREFIX = "auth:roles:";
    private static final String PERM_KEY_PREFIX = "auth:perms:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PermissionCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 读取用户角色编码，未命中时通过 loader 查库并回填。 */
    public List<String> getRoleCodes(Long userId, Supplier<List<String>> loader) {
        return getOrLoad(ROLE_KEY_PREFIX + userId, loader);
    }

    /** 读取用户权限编码，未命中时通过 loader 查库并回填。 */
    public List<String> getPermissionCodes(Long userId, Supplier<List<String>> loader) {
        return getOrLoad(PERM_KEY_PREFIX + userId, loader);
    }

    /** 失效单个用户的角色与权限缓存（用户角色变更时调用）。 */
    public void evictUser(Long userId) {
        safeDelete(List.of(ROLE_KEY_PREFIX + userId, PERM_KEY_PREFIX + userId));
    }

    /**
     * 失效全部用户的角色与权限缓存。
     *
     * <p>用于角色 / 权限本身发生变更（影响面无法精确到单个用户）的场景。这些都是
     * 低频管理操作，且 RBAC 数据量小，使用 keys 扫描可接受。
     */
    public void evictAll() {
        try {
            Set<String> keys = redisTemplate.keys(ROLE_KEY_PREFIX + "*");
            Set<String> permKeys = redisTemplate.keys(PERM_KEY_PREFIX + "*");
            if (keys != null && permKeys != null) {
                keys.addAll(permKeys);
            } else if (permKeys != null) {
                keys = permKeys;
            }
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("清空权限缓存失败，已忽略（缓存将随 TTL 过期）", ex);
        }
    }

    private List<String> getOrLoad(String key, Supplier<List<String>> loader) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                List<String> codes = objectMapper.readValue(cached, STRING_LIST_TYPE);
                if (isValidCodeList(codes)) {
                    return codes;
                }
                deleteQuietly(key);
            }
        } catch (Exception ex) {
            deleteQuietly(key);
            log.warn("读取权限缓存失败，已删除坏缓存并回退数据库 key={} error={}", key, ex.getMessage());
        }
        List<String> value = loader.get();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL);
        } catch (Exception ex) {
            log.warn("写入权限缓存失败 key={}", key, ex);
        }
        return value;
    }

    private boolean isValidCodeList(Collection<String> codes) {
        return codes != null && codes.stream().allMatch(code -> code != null && !code.isBlank());
    }

    private void deleteQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("删除坏权限缓存失败 key={}", key, ex);
        }
    }

    private void safeDelete(List<String> keys) {
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.warn("删除权限缓存失败 keys={}", keys, ex);
        }
    }
}

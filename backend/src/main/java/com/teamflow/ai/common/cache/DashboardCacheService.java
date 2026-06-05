package com.teamflow.ai.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 工作台统计缓存。
 *
 * <p>工作台的总览数字、趋势图、活跃度等都是<b>全局聚合查询</b>（多次 {@code COUNT}、
 * {@code GROUP BY}、{@code JOIN}），查询成本高但对实时性要求低，几分钟的陈旧完全可接受。
 * 因此采用纯 TTL 过期策略：不在每个写入点（任务/项目/用户增删）做主动失效，统一靠较短 TTL
 * 兜底，换取实现简单与零侵入。
 *
 * <p>序列化与降级逻辑统一委托 {@link JsonCacheService}（StringRedisTemplate + JavaTime 的
 * 普通 JSON），本类只保留工作台语义的薄封装。
 */
@Service
public class DashboardCacheService {

    private final JsonCacheService jsonCacheService;

    public DashboardCacheService(JsonCacheService jsonCacheService) {
        this.jsonCacheService = jsonCacheService;
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
        return jsonCacheService.getOrLoad(key, ttl, type, loader);
    }
}

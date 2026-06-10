package com.teamflow.ai.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 工作台统计缓存。
 *
 * <p>工作台的总览数字、趋势图、活跃度等都是<b>全局聚合查询</b>（多次 {@code COUNT}、
 * {@code GROUP BY}、{@code JOIN}），查询成本高。采用「写时主动失效 + TTL 兜底」策略：
 * 任务/项目/用户/知识库/AI 等写入点调用对应的 {@code evictXxxStats()} 删除受影响的键，
 * 保证工作台数据实时；TTL 仅作为失效遗漏时的兜底。
 *
 * <p>各缓存键与底层数据的依赖关系：
 * <ul>
 *   <li>{@link #KEY_OVERVIEW}：用户数、项目数、任务数、知识文档数、AI 消息数 —— 任一域写入都受影响</li>
 *   <li>{@link #KEY_TASK_TREND}：近 7 天每日新建任务数 —— 仅任务写入受影响</li>
 *   <li>{@link #KEY_MEMBER_ACTIVE}：按负责人聚合任务数（展示用户昵称）—— 任务与用户写入受影响</li>
 *   <li>{@link #KEY_AI_USAGE}：按会话类型聚合 AI 会话数 —— AI 会话写入受影响</li>
 *   <li>{@link #KEY_TODOS}：未完成任务列表（展示项目名、用户昵称）—— 任务/项目/用户写入受影响</li>
 * </ul>
 *
 * <p>序列化与降级逻辑统一委托 {@link JsonCacheService}（StringRedisTemplate + JavaTime 的
 * 普通 JSON），本类只保留工作台语义的薄封装。
 */
@Service
public class DashboardCacheService {

    public static final String KEY_OVERVIEW = "dashboard:overview";
    public static final String KEY_TASK_TREND = "dashboard:project-trend";
    public static final String KEY_MEMBER_ACTIVE = "dashboard:member-active";
    public static final String KEY_AI_USAGE = "dashboard:ai-usage";
    public static final String KEY_TODOS = "dashboard:todos";

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

    /** 任务新增/修改/删除/状态变更后调用：影响总览、任务趋势、成员活跃度、待办列表。 */
    public void evictTaskStats() {
        jsonCacheService.evict(KEY_OVERVIEW);
        jsonCacheService.evict(KEY_TASK_TREND);
        jsonCacheService.evict(KEY_MEMBER_ACTIVE);
        jsonCacheService.evict(KEY_TODOS);
    }

    /** 项目新增/修改/删除后调用：影响总览（项目数）、待办列表（展示项目名）。 */
    public void evictProjectStats() {
        jsonCacheService.evict(KEY_OVERVIEW);
        jsonCacheService.evict(KEY_TODOS);
    }

    /** 用户新增/修改（昵称、状态）后调用：影响总览（用户数）、成员活跃度与待办列表（展示昵称）。 */
    public void evictUserStats() {
        jsonCacheService.evict(KEY_OVERVIEW);
        jsonCacheService.evict(KEY_MEMBER_ACTIVE);
        jsonCacheService.evict(KEY_TODOS);
    }

    /** 知识文档新增/删除（含空间级联删除）后调用：影响总览（文档数）。 */
    public void evictKnowledgeStats() {
        jsonCacheService.evict(KEY_OVERVIEW);
    }

    /** AI 会话/消息写入后调用：影响总览（AI 消息数）、AI 使用分布（会话类型聚合）。 */
    public void evictAiStats() {
        jsonCacheService.evict(KEY_OVERVIEW);
        jsonCacheService.evict(KEY_AI_USAGE);
    }

    /** 删除全部工作台缓存（演示数据初始化等批量写入场景）。 */
    public void evictAll() {
        jsonCacheService.evictByPrefix("dashboard:");
    }
}

package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 会话列表项 DTO，用于前端侧边栏展示和会话管理接口。
 * <p>
 * 包含会话元信息及冗余展示字段（username、spaceName），避免前端二次查询。
 * messageCount 是冗余统计字段，由 SQL 聚合计算，不存在 ai_session 表中。
 */
public record AiSessionItem(
        Long id,
        Long userId,
        /** 用户名，JOIN 查询冗余，避免前端再次请求用户信息。 */
        String username,
        Long spaceId,
        /** 知识空间名称，JOIN 查询冗余；无绑定空间时为 null。 */
        String spaceName,
        /** 会话标题，首次由 AI 自动生成，后续可手动修改。 */
        String sessionTitle,
        String modelName,
        String sessionType,
        /** 该会话下的消息总条数（含 user 和 assistant 双方），用于前端展示。 */
        long messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

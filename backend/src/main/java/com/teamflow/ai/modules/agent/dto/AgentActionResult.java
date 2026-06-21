package com.teamflow.ai.modules.agent.dto;

/**
 * Agent 写操作确认/取消后的返回体。
 *
 * <p>targetType/targetId/url 让前端在确认执行后能跳转到被创建或被修改的业务对象。</p>
 */
public record AgentActionResult(
        Long actionId,
        String status,
        String summary,
        String targetType,
        Long targetId,
        String url
) {
}

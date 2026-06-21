package com.teamflow.ai.modules.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Agent 模式聊天请求。
 *
 * <p>Agent 固定走工具调用编排，useKnowledge 只作为会话上下文偏好保存；
 * 是否检索知识库由模型根据提示词主动调用 search_knowledge 工具决定。</p>
 */
public record AgentChatRequest(
        Long sessionId,
        Long spaceId,
        String model,
        Boolean useKnowledge,
        @NotBlank(message = "请输入消息内容") String message
) {
}

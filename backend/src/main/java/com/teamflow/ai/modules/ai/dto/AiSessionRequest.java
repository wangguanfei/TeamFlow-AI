package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建/更新 AI 会话的请求体（POST/PUT /api/ai/sessions）。
 * <p>
 * 通常在用户手动新建会话或修改会话标题时使用；
 * 对话接口（/api/ai/chat）会自动创建会话，无需提前调用本接口。
 */
public record AiSessionRequest(
        /** 绑定的知识空间 ID，为 null 则表示不启用 RAG 知识库能力。 */
        Long spaceId,
        @NotBlank(message = "会话标题不能为空") String sessionTitle,
        /** 指定模型名，为 null 时使用系统默认模型（AiProperties.model）。 */
        String modelName,
        /** 会话类型，控制 system prompt 风格，参见 AiSession.sessionType 说明。 */
        String sessionType
) {
}

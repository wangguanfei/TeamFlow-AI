package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 前端发起一次 AI 对话的请求体。
 *
 * <p>mode 决定系统提示词场景，useKnowledge 决定是否检索知识库；两者解耦后，
 * 用户可以在普通问答/总结/代码模式里独立开启或关闭 RAG。</p>
 */
public record AiChatRequest(
        Long sessionId,
        Long spaceId,
        String mode,
        Boolean useKnowledge,
        String model,
        @NotBlank(message = "请输入消息内容") String message
) {
}

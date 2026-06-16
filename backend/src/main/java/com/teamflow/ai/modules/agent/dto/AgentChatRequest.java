package com.teamflow.ai.modules.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(
        Long sessionId,
        Long spaceId,
        String model,
        Boolean useKnowledge,
        @NotBlank(message = "请输入消息内容") String message
) {
}

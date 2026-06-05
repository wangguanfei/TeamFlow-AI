package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        Long sessionId,
        Long spaceId,
        String mode,
        Boolean useKnowledge,
        String model,
        @NotBlank(message = "请输入消息内容") String message
) {
}

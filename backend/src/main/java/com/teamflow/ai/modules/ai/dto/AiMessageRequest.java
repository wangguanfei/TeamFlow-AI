package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiMessageRequest(
        @NotNull(message = "会话不能为空") Long sessionId,
        @NotBlank(message = "角色不能为空") String role,
        @NotBlank(message = "消息内容不能为空") String content,
        Integer tokens,
        String referencesJson
) {
}

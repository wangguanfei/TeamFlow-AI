package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiSessionRequest(
        Long spaceId,
        @NotBlank(message = "会话标题不能为空") String sessionTitle,
        String modelName,
        String sessionType
) {
}

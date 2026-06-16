package com.teamflow.ai.modules.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentCancelRequest(
        @NotBlank(message = "确认令牌不能为空") String confirmToken
) {
}

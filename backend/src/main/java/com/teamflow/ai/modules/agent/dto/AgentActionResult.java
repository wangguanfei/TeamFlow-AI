package com.teamflow.ai.modules.agent.dto;

public record AgentActionResult(
        Long actionId,
        String status,
        String summary,
        String targetType,
        Long targetId,
        String url
) {
}

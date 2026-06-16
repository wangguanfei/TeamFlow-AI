package com.teamflow.ai.modules.agent.dto;

import java.time.LocalDateTime;

public record AgentActionPageItem(
        Long id,
        Long sessionId,
        Long messageId,
        Long userId,
        String username,
        Long confirmedBy,
        String confirmedByUsername,
        String toolName,
        String toolLabel,
        String argumentsJson,
        String previewJson,
        String resultJson,
        Integer isWrite,
        String status,
        LocalDateTime confirmedAt,
        String targetType,
        Long targetId,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

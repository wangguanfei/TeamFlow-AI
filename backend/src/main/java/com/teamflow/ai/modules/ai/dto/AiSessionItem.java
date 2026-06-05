package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

public record AiSessionItem(
        Long id,
        Long userId,
        String username,
        Long spaceId,
        String spaceName,
        String sessionTitle,
        String modelName,
        String sessionType,
        long messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

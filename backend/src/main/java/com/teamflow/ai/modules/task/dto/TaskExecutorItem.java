package com.teamflow.ai.modules.task.dto;

import java.time.LocalDateTime;

public record TaskExecutorItem(
        Long id,
        Long taskId,
        Long userId,
        String username,
        String nickname,
        String displayName,
        LocalDateTime createdAt
) {
}

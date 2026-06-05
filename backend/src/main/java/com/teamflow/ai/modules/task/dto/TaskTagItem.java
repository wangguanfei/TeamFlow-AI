package com.teamflow.ai.modules.task.dto;

import java.time.LocalDateTime;

public record TaskTagItem(
        Long id,
        Long taskId,
        String tagName,
        String tagColor,
        LocalDateTime createdAt
) {
}

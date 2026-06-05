package com.teamflow.ai.modules.task.dto;

import java.time.LocalDateTime;

public record TaskCommentItem(
        Long id,
        Long taskId,
        Long userId,
        String username,
        String nickname,
        String content,
        LocalDateTime createdAt
) {
}

package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

public record AiMessageFeedbackItem(
        Long id,
        Long messageId,
        Long userId,
        Integer rating,
        String reason,
        Long expectedDocId,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

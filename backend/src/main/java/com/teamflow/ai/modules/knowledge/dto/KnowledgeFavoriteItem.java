package com.teamflow.ai.modules.knowledge.dto;

import java.time.LocalDateTime;

public record KnowledgeFavoriteItem(
        Long id,
        Long docId,
        String title,
        Long userId,
        LocalDateTime createdAt
) {
}

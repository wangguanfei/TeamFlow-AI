package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

public record AiEmbeddingItem(
        Long id,
        Long docId,
        Integer chunkIndex,
        String chunkText,
        String embeddingHash,
        String embeddingText,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

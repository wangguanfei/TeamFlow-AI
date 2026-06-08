package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

public record AiEmbeddingItem(
        Long id,
        Long docId,
        Integer chunkIndex,
        String chunkText,
        String embeddingHash,
        String embeddingText,
        String embeddingModel,
        Integer embeddingDim,
        String vectorPointId,
        String contentHash,
        String indexStatus,
        LocalDateTime indexedAt,
        String indexError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

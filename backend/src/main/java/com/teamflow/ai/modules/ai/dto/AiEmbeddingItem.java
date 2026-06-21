package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

/**
 * 向量嵌入记录 DTO，用于管理接口查看嵌入状态（GET /api/ai/embeddings）。
 * 字段说明参见 {@link com.teamflow.ai.modules.ai.entity.AiEmbedding} 实体类注释。
 */
public record AiEmbeddingItem(
        Long id,
        Long docId,
        Integer chunkIndex,
        String chunkText,
        String embeddingHash,
        String embeddingText,
        String embeddingModel,
        Integer embeddingDim,
        /** Qdrant point id，用于向量的增删改查和回溯定位。 */
        String vectorPointId,
        String contentHash,
        /** 索引状态：PENDING / READY / FAILED / MANUAL。 */
        String indexStatus,
        LocalDateTime indexedAt,
        /** 索引失败时的错误信息，READY 时为 null。 */
        String indexError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

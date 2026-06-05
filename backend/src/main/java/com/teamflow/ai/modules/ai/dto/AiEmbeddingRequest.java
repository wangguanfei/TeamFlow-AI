package com.teamflow.ai.modules.ai.dto;

public record AiEmbeddingRequest(
        Long docId,
        Integer chunkIndex,
        String chunkText,
        String embeddingHash,
        String embeddingText
) {
}

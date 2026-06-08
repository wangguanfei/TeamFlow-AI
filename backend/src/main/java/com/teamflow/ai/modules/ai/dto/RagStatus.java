package com.teamflow.ai.modules.ai.dto;

public record RagStatus(
        boolean enabled,
        boolean workerEnabled,
        boolean qdrantAvailable,
        boolean embeddingAvailable,
        boolean memoryGatePassed,
        long memAvailableMb,
        long pendingJobs,
        long runningJobs,
        long failedJobs,
        String qdrantCollection,
        String embeddingModel
) {
}

package com.teamflow.ai.modules.ai.dto;

/**
 * RAG 运维状态。
 *
 * <p>用于前端和接口验收确认：总开关、worker、Qdrant、Embedding、内存闸门以及索引任务积压。</p>
 */
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

package com.teamflow.ai.modules.ai.dto;

/**
 * RAG 索引重建接口的响应体（POST /api/rag/index/rebuild）。
 * <p>
 * 重建操作是异步的：接口返回后任务已入队，实际向量化由后台 worker 完成。
 * 前端可通过 GET /api/rag/status 中的 pendingJobs 字段轮询进度。
 */
public record RagRebuildResponse(
        /** 本次触发重建后新入队的任务数量，可用于判断是否真正触发了重建。 */
        long queuedJobs
) {
}

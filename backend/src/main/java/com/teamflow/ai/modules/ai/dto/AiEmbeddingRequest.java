package com.teamflow.ai.modules.ai.dto;

/**
 * 手动写入向量嵌入记录的请求体（POST /api/ai/embeddings）。
 * <p>
 * 通常由管理接口或测试脚本调用，正常向量化流程由 AiKnowledgeIndexService 内部触发。
 */
public record AiEmbeddingRequest(
        /** 来源文档 ID。 */
        Long docId,
        /** 切片序号，同一文档从 0 开始递增。 */
        Integer chunkIndex,
        /** 实际送入 embedding 模型的文本片段。 */
        String chunkText,
        /** 切片内容的 hash，用于判断是否需要重新向量化。 */
        String embeddingHash,
        /** 早期调试用的文本向量表示，已被 Qdrant 向量存储替代，通常为 null。 */
        String embeddingText
) {
}

package com.teamflow.ai.modules.ai.dto;

/**
 * 一条 RAG 引用来源。
 *
 * <p>它既会写入 ai_message.references_json 作为历史快照，也会返回给前端引用卡片和评测脚本。
 * chunkId 对应 Qdrant point id 或降级关键词 key，retrievalSource 用于判断 VECTOR/KEYWORD/HYBRID。</p>
 */
public record AiReferenceItem(
        Long docId,
        String title,
        String snippet,
        Long spaceId,
        String spaceName,
        Integer versionNo,
        Integer chunkIndex,
        Double score,
        String chunkId,
        Double denseScore,
        Double keywordScore,
        String retrievalSource
) {
    public AiReferenceItem(
            Long docId,
            String title,
            String snippet,
            Long spaceId,
            String spaceName,
            Integer versionNo,
            Integer chunkIndex,
            Double score
    ) {
        this(docId, title, snippet, spaceId, spaceName, versionNo, chunkIndex, score, null, null, null, null);
    }
}

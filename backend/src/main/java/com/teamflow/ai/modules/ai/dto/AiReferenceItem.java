package com.teamflow.ai.modules.ai.dto;

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

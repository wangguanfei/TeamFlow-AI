package com.teamflow.ai.modules.ai.dto;

public record AiReferenceItem(
        Long docId,
        String title,
        String snippet,
        Long spaceId,
        String spaceName,
        Integer versionNo,
        Integer chunkIndex,
        Double score
) {
}

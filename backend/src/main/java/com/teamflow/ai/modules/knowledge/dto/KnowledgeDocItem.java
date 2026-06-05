package com.teamflow.ai.modules.knowledge.dto;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeDocItem(
        Long id,
        Long spaceId,
        String spaceName,
        Long parentId,
        String title,
        String contentMd,
        String contentText,
        Long authorId,
        String authorName,
        String docStatus,
        Integer sortNo,
        Integer versionNo,
        boolean favorite,
        Long favoriteId,
        List<KnowledgeTagItem> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

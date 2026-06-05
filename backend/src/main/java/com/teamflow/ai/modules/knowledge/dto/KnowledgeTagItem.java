package com.teamflow.ai.modules.knowledge.dto;

import java.time.LocalDateTime;

public record KnowledgeTagItem(
        Long id,
        Long docId,
        String tagName,
        LocalDateTime createdAt
) {
}

package com.teamflow.ai.modules.knowledge.dto;

import java.time.LocalDateTime;

public record KnowledgeVersionItem(
        Long id,
        Long docId,
        Integer versionNo,
        String title,
        String contentMd,
        Long editorId,
        String editorName,
        String changeSummary,
        LocalDateTime createdAt
) {
}

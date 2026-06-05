package com.teamflow.ai.modules.knowledge.dto;

import com.teamflow.ai.modules.file.dto.FileItem;

public record KnowledgeImportResult(
        KnowledgeDocItem doc,
        FileItem file,
        boolean published,
        boolean indexed,
        String extractMode
) {
}

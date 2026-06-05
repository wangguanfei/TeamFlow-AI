package com.teamflow.ai.modules.knowledge.dto;

import jakarta.validation.constraints.NotNull;

public record KnowledgeFavoriteRequest(
        @NotNull(message = "文档ID不能为空") Long docId
) {
}

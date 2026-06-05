package com.teamflow.ai.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KnowledgeTagRequest(
        @NotNull(message = "文档ID不能为空") Long docId,
        @NotBlank(message = "标签名称不能为空") String tagName
) {
}

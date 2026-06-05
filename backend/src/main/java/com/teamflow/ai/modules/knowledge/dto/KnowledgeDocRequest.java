package com.teamflow.ai.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record KnowledgeDocRequest(
        @NotNull(message = "知识空间不能为空") Long spaceId,
        Long parentId,
        @NotBlank(message = "文档标题不能为空") String title,
        String contentMd,
        String docStatus,
        Integer sortNo,
        List<String> tags
) {
}

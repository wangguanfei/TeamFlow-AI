package com.teamflow.ai.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeSpaceRequest(
        Long teamId,
        @NotBlank(message = "空间名称不能为空") String spaceName,
        String description,
        String visibility
) {
}

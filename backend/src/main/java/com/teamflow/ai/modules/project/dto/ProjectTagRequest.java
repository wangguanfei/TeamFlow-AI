package com.teamflow.ai.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProjectTagRequest(
        @NotNull(message = "项目ID不能为空") Long projectId,
        @NotBlank(message = "标签名不能为空") String tagName,
        String tagColor
) {
}

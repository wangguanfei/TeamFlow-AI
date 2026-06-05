package com.teamflow.ai.modules.task.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskTagRequest(
        Long taskId,
        @NotBlank(message = "标签名不能为空") String tagName,
        String tagColor
) {
}

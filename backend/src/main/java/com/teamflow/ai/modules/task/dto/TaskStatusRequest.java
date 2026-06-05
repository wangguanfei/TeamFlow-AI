package com.teamflow.ai.modules.task.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskStatusRequest(
        @NotBlank(message = "状态不能为空") String status,
        Integer sortNo
) {
}

package com.teamflow.ai.modules.task.dto;

import jakarta.validation.constraints.NotNull;

public record TaskAttachmentRequest(
        @NotNull(message = "任务ID不能为空") Long taskId,
        @NotNull(message = "文件ID不能为空") Long fileId
) {
}

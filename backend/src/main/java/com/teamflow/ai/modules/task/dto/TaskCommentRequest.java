package com.teamflow.ai.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskCommentRequest(
        @NotNull(message = "任务ID不能为空") Long taskId,
        @NotBlank(message = "评论内容不能为空") String content
) {
}

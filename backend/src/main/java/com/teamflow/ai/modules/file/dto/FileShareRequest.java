package com.teamflow.ai.modules.file.dto;

import jakarta.validation.constraints.NotNull;

public record FileShareRequest(
        @NotNull(message = "文件ID不能为空") Long fileId,
        Integer expireDays
) {
}

package com.teamflow.ai.modules.project.dto;

import jakarta.validation.constraints.NotNull;

public record ProjectMemberRequest(
        @NotNull(message = "项目ID不能为空") Long projectId,
        @NotNull(message = "用户ID不能为空") Long userId,
        String projectRole
) {
}

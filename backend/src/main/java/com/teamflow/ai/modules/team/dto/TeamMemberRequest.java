package com.teamflow.ai.modules.team.dto;

import jakarta.validation.constraints.NotNull;

public record TeamMemberRequest(
        @NotNull(message = "用户ID不能为空") Long userId,
        String memberRole
) {
}

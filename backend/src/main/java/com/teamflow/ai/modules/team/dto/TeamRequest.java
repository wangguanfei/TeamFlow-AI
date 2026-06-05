package com.teamflow.ai.modules.team.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamRequest(
        @NotBlank(message = "团队名称不能为空") String teamName,
        @NotBlank(message = "团队编码不能为空") String teamCode,
        Long ownerId,
        String description,
        Integer status
) {
}

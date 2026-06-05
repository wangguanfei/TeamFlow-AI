package com.teamflow.ai.modules.project.dto;

import java.time.LocalDateTime;

public record ProjectMemberItem(
        Long id,
        Long projectId,
        Long userId,
        String username,
        String nickname,
        String email,
        String projectRole,
        LocalDateTime createdAt
) {
}

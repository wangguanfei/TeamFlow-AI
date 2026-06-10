package com.teamflow.ai.modules.team.dto;

import java.time.LocalDateTime;

public record TeamMemberItem(
        Long id,
        Long teamId,
        Long userId,
        String username,
        String nickname,
        String memberRole,
        LocalDateTime joinTime,
        Integer status
) {
}

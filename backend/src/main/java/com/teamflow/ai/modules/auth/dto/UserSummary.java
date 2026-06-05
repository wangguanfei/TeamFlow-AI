package com.teamflow.ai.modules.auth.dto;

public record UserSummary(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        String mobile
) {
}

package com.teamflow.ai.modules.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        String mobile,
        Integer status,
        LocalDateTime lastLoginTime,
        String lastLoginIp,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> roles,
        List<String> permissions
) {
}

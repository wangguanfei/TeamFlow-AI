package com.teamflow.ai.modules.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserPageItem(
        Long id,
        String username,
        String nickname,
        String email,
        String mobile,
        Integer status,
        LocalDateTime lastLoginTime,
        List<String> roles
) {
}

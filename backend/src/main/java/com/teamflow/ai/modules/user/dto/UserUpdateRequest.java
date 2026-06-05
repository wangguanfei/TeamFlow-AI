package com.teamflow.ai.modules.user.dto;

public record UserUpdateRequest(
        String nickname,
        String email,
        String mobile,
        Integer status
) {
}

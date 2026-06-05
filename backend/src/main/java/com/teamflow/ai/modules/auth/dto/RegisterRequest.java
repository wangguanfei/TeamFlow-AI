package com.teamflow.ai.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入账号") String username,
        @NotBlank(message = "请输入密码")
        @Size(min = 6, message = "密码长度不能少于6位")
        String password,
        String nickname,
        String email,
        String mobile
) {
}

package com.teamflow.ai.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordResetRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "密码长度不能少于6位")
        String password
) {
}

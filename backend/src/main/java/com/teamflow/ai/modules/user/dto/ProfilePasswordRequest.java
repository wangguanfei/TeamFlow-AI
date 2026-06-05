package com.teamflow.ai.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfilePasswordRequest(
        @NotBlank(message = "请输入旧密码")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位")
        String newPassword
) {
}

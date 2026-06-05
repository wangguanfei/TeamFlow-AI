package com.teamflow.ai.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 50, message = "昵称不能超过 50 个字符")
        String nickname,

        @Size(max = 255, message = "头像地址不能超过 255 个字符")
        String avatarUrl,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过 100 个字符")
        String email,

        @Size(max = 30, message = "手机号不能超过 30 个字符")
        String mobile
) {
}

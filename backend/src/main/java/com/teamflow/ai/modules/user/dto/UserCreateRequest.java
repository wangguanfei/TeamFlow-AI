package com.teamflow.ai.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserCreateRequest(
        @NotBlank(message = "账号不能为空") String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, message = "密码长度不能少于6位")
        String password,
        String nickname,
        String email,
        String mobile,
        Integer status,
        List<Long> roleIds
) {
}

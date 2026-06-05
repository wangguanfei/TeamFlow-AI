package com.teamflow.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;

public record PermissionRequest(
        @NotBlank(message = "权限编码不能为空") String permissionCode,
        @NotBlank(message = "权限名称不能为空") String permissionName,
        String resourceType,
        String resourcePath
) {
}

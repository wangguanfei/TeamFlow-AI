package com.teamflow.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
        @NotBlank(message = "角色编码不能为空") String roleCode,
        @NotBlank(message = "角色名称不能为空") String roleName,
        String scopeType,
        Integer sortNo,
        Integer status,
        String remark
) {
}

package com.teamflow.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;

public record MenuRequest(
        Long parentId,
        @NotBlank(message = "菜单名称不能为空") String menuName,
        @NotBlank(message = "路由不能为空") String path,
        String component,
        String icon,
        String permissionCode,
        String menuType,
        Integer sortNo,
        Integer visible
) {
}

package com.teamflow.ai.modules.auth.dto;

import java.util.List;

public record MenuRoute(
        Long id,
        Long parentId,
        String name,
        String path,
        String component,
        String icon,
        String permissionCode,
        String type,
        Integer sortNo,
        List<MenuRoute> children
) {
}

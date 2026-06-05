package com.teamflow.ai.modules.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        UserSummary user,
        List<String> roles,
        List<String> permissions,
        List<MenuRoute> menus
) {
}

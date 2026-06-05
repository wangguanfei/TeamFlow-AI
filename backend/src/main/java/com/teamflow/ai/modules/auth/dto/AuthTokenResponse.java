package com.teamflow.ai.modules.auth.dto;

import java.util.List;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user,
        List<String> roles,
        List<String> permissions,
        List<MenuRoute> menus
) {
}

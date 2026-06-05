package com.teamflow.ai.common.security;

import java.time.Instant;

public record JwtClaims(
        Long userId,
        String username,
        JwtTokenType tokenType,
        Instant expiresAt
) {
}

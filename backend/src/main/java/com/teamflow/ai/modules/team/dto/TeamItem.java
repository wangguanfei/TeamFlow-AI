package com.teamflow.ai.modules.team.dto;

import java.time.LocalDateTime;

public record TeamItem(
        Long id,
        String teamName,
        String teamCode,
        Long ownerId,
        String ownerName,
        String description,
        Integer status,
        LocalDateTime createdAt
) {
}

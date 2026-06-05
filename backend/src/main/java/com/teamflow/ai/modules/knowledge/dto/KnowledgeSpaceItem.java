package com.teamflow.ai.modules.knowledge.dto;

import java.time.LocalDateTime;

public record KnowledgeSpaceItem(
        Long id,
        Long teamId,
        String spaceName,
        String description,
        String visibility,
        Long ownerId,
        String ownerName,
        long docCount,
        LocalDateTime createdAt
) {
}

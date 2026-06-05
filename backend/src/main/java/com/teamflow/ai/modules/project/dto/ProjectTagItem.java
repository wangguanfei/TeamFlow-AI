package com.teamflow.ai.modules.project.dto;

import java.time.LocalDateTime;

public record ProjectTagItem(
        Long id,
        Long projectId,
        String tagName,
        String tagColor,
        LocalDateTime createdAt
) {
}

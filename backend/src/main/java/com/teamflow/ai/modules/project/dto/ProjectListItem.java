package com.teamflow.ai.modules.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectListItem(
        Long id,
        Long teamId,
        String teamName,
        String projectCode,
        String projectName,
        String description,
        Long ownerId,
        String ownerName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        BigDecimal progress,
        long memberCount,
        List<ProjectTagItem> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

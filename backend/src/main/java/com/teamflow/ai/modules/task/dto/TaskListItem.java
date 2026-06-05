package com.teamflow.ai.modules.task.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TaskListItem(
        Long id,
        Long projectId,
        String projectName,
        Long parentId,
        String taskNo,
        String title,
        String description,
        Long assigneeId,
        String assigneeName,
        List<Long> executorIds,
        List<String> executorNames,
        Long reporterId,
        String reporterName,
        String priority,
        String status,
        LocalDateTime startTime,
        LocalDateTime dueTime,
        BigDecimal estimateHours,
        BigDecimal actualHours,
        Integer sortNo,
        List<TaskTagItem> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

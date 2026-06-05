package com.teamflow.ai.modules.task.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GanttTaskItem(
        Long id,
        String taskNo,
        String title,
        String status,
        String assigneeName,
        LocalDateTime startTime,
        LocalDateTime dueTime,
        BigDecimal progress
) {
}

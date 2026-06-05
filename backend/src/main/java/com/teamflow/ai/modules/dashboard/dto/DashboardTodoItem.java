package com.teamflow.ai.modules.dashboard.dto;

import java.time.LocalDateTime;

public record DashboardTodoItem(
        Long id,
        String taskNo,
        String title,
        String status,
        String projectName,
        String assigneeName,
        LocalDateTime dueTime
) {
}

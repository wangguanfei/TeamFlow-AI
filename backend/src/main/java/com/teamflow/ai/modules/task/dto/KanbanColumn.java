package com.teamflow.ai.modules.task.dto;

import java.util.List;

public record KanbanColumn(
        String status,
        String title,
        List<TaskListItem> tasks
) {
}

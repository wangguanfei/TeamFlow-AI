package com.teamflow.ai.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TaskRequest(
        @NotNull(message = "项目不能为空") Long projectId,
        Long parentId,
        String taskNo,
        @NotBlank(message = "任务标题不能为空") String title,
        String description,
        Long assigneeId,
        List<Long> executorIds,
        String priority,
        String status,
        LocalDateTime startTime,
        LocalDateTime dueTime,
        BigDecimal estimateHours,
        Integer sortNo,
        List<TaskTagRequest> tags
) {
}

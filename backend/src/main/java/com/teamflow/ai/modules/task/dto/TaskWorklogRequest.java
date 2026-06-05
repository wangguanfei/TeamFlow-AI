package com.teamflow.ai.modules.task.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaskWorklogRequest(
        @NotNull(message = "任务ID不能为空") Long taskId,
        LocalDate workDate,
        @NotNull(message = "工时不能为空") BigDecimal hours,
        String description
) {
}

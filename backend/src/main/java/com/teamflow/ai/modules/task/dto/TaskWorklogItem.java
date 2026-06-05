package com.teamflow.ai.modules.task.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskWorklogItem(
        Long id,
        Long taskId,
        Long userId,
        String username,
        String nickname,
        LocalDate workDate,
        BigDecimal hours,
        String description,
        LocalDateTime createdAt
) {
}

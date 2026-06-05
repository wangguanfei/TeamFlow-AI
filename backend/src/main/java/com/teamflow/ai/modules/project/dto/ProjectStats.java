package com.teamflow.ai.modules.project.dto;

import java.math.BigDecimal;

public record ProjectStats(
        long total,
        long active,
        long done,
        BigDecimal averageProgress
) {
}

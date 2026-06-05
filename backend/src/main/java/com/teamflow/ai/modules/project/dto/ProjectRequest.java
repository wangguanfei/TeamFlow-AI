package com.teamflow.ai.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectRequest(
        @NotNull(message = "团队不能为空") Long teamId,
        @NotBlank(message = "项目编码不能为空") String projectCode,
        @NotBlank(message = "项目名称不能为空") String projectName,
        String description,
        Long ownerId,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        BigDecimal progress,
        List<Long> memberUserIds,
        List<ProjectTagRequest> tags
) {
}

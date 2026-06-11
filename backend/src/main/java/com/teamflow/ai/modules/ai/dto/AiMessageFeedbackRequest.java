package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AiMessageFeedbackRequest(
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最小为1")
        @Max(value = 5, message = "评分最大为5")
        Integer rating,
        String reason,
        Long expectedDocId,
        String comment
) {
}

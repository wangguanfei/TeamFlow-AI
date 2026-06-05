package com.teamflow.ai.modules.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationRequest(
        @NotBlank(message = "通知标题不能为空") String title,
        String content,
        String notifyType,
        String targetType,
        Long targetId
) {
}

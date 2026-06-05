package com.teamflow.ai.modules.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationReadRequest(
        @NotNull(message = "通知ID不能为空") Long notificationId
) {
}

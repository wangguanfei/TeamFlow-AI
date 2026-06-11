package com.teamflow.ai.modules.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record NotificationRequest(
        @NotBlank(message = "通知标题不能为空") String title,
        String content,
        String notifyType,
        String targetType,
        Long targetId,
        String bizType,
        Long bizId,
        LocalDateTime bizTime
) {
    public NotificationRequest(String title, String content, String notifyType, String targetType, Long targetId) {
        this(title, content, notifyType, targetType, targetId, null, null, null);
    }
}

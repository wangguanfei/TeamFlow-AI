package com.teamflow.ai.modules.notification.dto;

import java.time.LocalDateTime;

public record NotificationItem(
        Long id,
        String title,
        String content,
        String notifyType,
        String targetType,
        Long targetId,
        Long senderId,
        String senderName,
        boolean read,
        LocalDateTime readTime,
        LocalDateTime createdAt
) {
}

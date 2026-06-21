package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;

/**
 * 消息反馈记录 DTO，用于管理后台查看反馈列表（GET /api/ai/messages/{id}/feedback）。
 * 字段含义与 {@link AiMessageFeedbackRequest} 一致，新增 id/createdAt/updatedAt 等展示字段。
 */
public record AiMessageFeedbackItem(
        Long id,
        Long messageId,
        Long userId,
        Integer rating,
        String reason,
        Long expectedDocId,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.teamflow.ai.modules.task.dto;

import java.time.LocalDateTime;

public record TaskAttachmentItem(
        Long id,
        Long taskId,
        Long fileId,
        String fileName,
        String contentType,
        Long fileSize,
        String fileExt,
        Long createdBy,
        String uploaderName,
        LocalDateTime createdAt
) {
}

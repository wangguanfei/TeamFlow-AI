package com.teamflow.ai.modules.file.dto;

import java.time.LocalDateTime;

public record FileItem(
        Long id,
        String bizType,
        Long bizId,
        String bucketName,
        String objectKey,
        String originalName,
        String contentType,
        Long fileSize,
        String fileExt,
        Long uploaderId,
        String uploaderName,
        LocalDateTime createdAt
) {
}

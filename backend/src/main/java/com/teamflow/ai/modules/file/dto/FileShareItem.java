package com.teamflow.ai.modules.file.dto;

import java.time.LocalDateTime;

public record FileShareItem(
        Long id,
        Long fileId,
        String fileName,
        String shareCode,
        LocalDateTime expireTime,
        Long createdBy,
        String creatorName,
        LocalDateTime createdAt
) {
}

package com.teamflow.ai.modules.file.dto;

public record FileUpdateRequest(
        String bizType,
        Long bizId,
        String originalName
) {
}

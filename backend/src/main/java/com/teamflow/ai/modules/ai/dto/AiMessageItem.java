package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiMessageItem(
        Long id,
        Long sessionId,
        String role,
        String content,
        Integer tokens,
        List<AiReferenceItem> references,
        LocalDateTime createdAt
) {
}

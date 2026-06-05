package com.teamflow.ai.modules.ai.dto;

import java.util.List;

public record AiChatResponse(
        AiSessionItem session,
        AiMessageItem userMessage,
        AiMessageItem assistantMessage,
        List<AiReferenceItem> references,
        boolean mock
) {
}

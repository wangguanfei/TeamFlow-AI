package com.teamflow.ai.modules.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 消息的前端展示 DTO，对应 ai_message 表的一条记录。
 * <p>
 * 用于会话历史消息列表（GET /api/ai/messages）和对话接口响应体（{@link AiChatResponse}）。
 * references 字段是从 referencesJson 反序列化的结构化列表，方便前端直接渲染引用卡片。
 */
public record AiMessageItem(
        Long id,
        Long sessionId,
        /** 消息角色：user / assistant。 */
        String role,
        /** 消息正文，Markdown 格式，前端负责渲染。 */
        String content,
        /** 估算 token 数，用于显示和配额统计。 */
        Integer tokens,
        /** 关联的 RAG 知识引用列表，user 消息此字段为空。 */
        List<AiReferenceItem> references,
        LocalDateTime createdAt
) {
}

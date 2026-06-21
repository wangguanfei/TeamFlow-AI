package com.teamflow.ai.modules.ai.dto;

import java.util.List;

/**
 * 阻塞式 AI 对话接口（POST /api/ai/chat）的响应体。
 * <p>
 * 一次对话同时返回：会话信息（自动创建时才新增）、用户消息持久化结果、
 * AI 回复消息、RAG 检索引用列表，以及当前是否走 Mock 模式的标志。
 * 流式接口（SSE）不使用本类，直接推送 token 事件。
 */
public record AiChatResponse(
        /** 会话信息，会话不存在时自动创建并返回新会话。 */
        AiSessionItem session,
        /** 持久化后的用户消息。 */
        AiMessageItem userMessage,
        /** AI 生成的完整回复，流式结束后写入数据库。 */
        AiMessageItem assistantMessage,
        /** RAG 检索到的知识引用列表，未开启知识库时为空列表。 */
        List<AiReferenceItem> references,
        /** 是否使用了 MockAiProvider（未配置 AI_API_KEY 时为 true）。 */
        boolean mock
) {
}

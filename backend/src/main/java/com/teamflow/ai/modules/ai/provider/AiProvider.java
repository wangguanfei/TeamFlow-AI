package com.teamflow.ai.modules.ai.provider;

import com.teamflow.ai.modules.ai.dto.AiReferenceItem;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 模型供应商抽象。
 *
 * <p>业务层只依赖这个接口，不关心底层是 DeepSeek、OpenAI-compatible 服务还是 Mock。
 * 这样本地演示、线上真实模型、Agent tool_call 能复用同一套会话编排逻辑。</p>
 */
public interface AiProvider {

    /**
     * 一次性返回完整回答，适合非流式接口和内部降级调用。
     */
    AiAnswer chat(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references, String model);

    /**
     * Agent 模式的工具调用入口。默认实现退化成普通聊天，只有支持 function/tool calling
     * 的 Provider 才需要覆盖它。
     */
    default AiAgentAnswer chatWithTools(List<AiPromptMessage> messages, List<AiToolDefinition> tools, String model) {
        AiAnswer answer = chat(messages, "AGENT", List.of(), model);
        return new AiAgentAnswer(answer.content(), List.of(), answer.tokens(), answer.modelName(), answer.mock());
    }

    /**
     * 流式回答入口。默认实现把完整答案一次性推给 tokenConsumer，保证 Mock 或不支持流式的
     * Provider 也能被前端 SSE 管线消费。
     */
    default AiAnswer chatStream(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references,
                                String model, Consumer<String> tokenConsumer) {
        AiAnswer answer = chat(messages, mode, references, model);
        tokenConsumer.accept(answer.content());
        return answer;
    }

    /** 一条对话消息，对应 OpenAI Messages API 的 role+content 结构。 */
    record AiPromptMessage(
            /** 消息角色：USER / ASSISTANT / SYSTEM（内部统一大写，Provider 发给上游前转小写）。 */
            String role,
            String content
    ) {}

    /** AI 模型的完整回答结果，供服务层落库和 SSE 事件组装使用。 */
    record AiAnswer(
            /** 完整回复正文（非流式），流式调用结束后由 fullContent 拼装而成。 */
            String content,
            /** 估算消耗的 token 数，用于配额统计（当前为字符数/4 的粗略估算）。 */
            int tokens,
            /** 实际使用的模型名称，Mock 模式下为 "mock-ai"。 */
            String modelName,
            /** 是否为 Mock 回答，前端据此展示提示条。 */
            boolean mock
    ) {}

    /** Agent 工具定义，对应 OpenAI function calling 的 tools[*].function 结构。 */
    record AiToolDefinition(
            /** 工具名称，必须与 AgentTool.definition().name() 完全一致。 */
            String name,
            /** 工具功能描述，大模型根据此描述决定是否调用该工具。 */
            String description,
            /** 工具参数的 JSON Schema，用于告知大模型如何传参。 */
            Map<String, Object> parameters
    ) {}

    /** 大模型决定调用的一次工具请求，由 AgentOrchestrator 解析后分发执行。 */
    record AiToolCall(
            /** 本次调用的唯一 ID，用于匹配 tool_result 消息（OpenAI 协议要求）。 */
            String id,
            /** 工具名称，与 AiToolDefinition.name 对应。 */
            String name,
            /** 大模型传入的工具参数，由各 AgentTool 实现自行解析。 */
            Map<String, Object> arguments
    ) {}

    /** Agent 模式的回答结果，可能同时包含文字回复和工具调用请求。 */
    record AiAgentAnswer(
            /** 文字回复部分（如果有），工具调用场景下通常为空字符串。 */
            String content,
            /** 大模型请求调用的工具列表，普通对话时为空列表。 */
            List<AiToolCall> toolCalls,
            int tokens,
            String modelName,
            boolean mock
    ) {}
}

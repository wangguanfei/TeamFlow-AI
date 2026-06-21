package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 手动写入消息的请求体（POST /api/ai/messages）。
 * <p>
 * 通常用于测试或管理接口手动录入对话记录；
 * 正常对话流程中消息由 AiService.chat()/chatStream() 内部自动创建，不走本接口。
 */
public record AiMessageRequest(
        @NotNull(message = "会话不能为空") Long sessionId,
        /** 消息角色：user / assistant / system。 */
        @NotBlank(message = "角色不能为空") String role,
        @NotBlank(message = "消息内容不能为空") String content,
        /** 预估 token 数，可由调用方提供或留 null 由服务端估算。 */
        Integer tokens,
        /** RAG 引用的 JSON 字符串，格式为 List<AiReferenceItem> 序列化结果。 */
        String referencesJson
) {
}

package com.teamflow.ai.modules.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 提交消息反馈的请求体（POST /api/ai/messages/{id}/feedback）。
 * <p>
 * 用于记录用户对 AI 回答的满意度评价。rating 为核心字段，其余字段为可选补充信息，
 * 用于分析 RAG 召回质量（expectedDocId 尤其重要）。
 */
public record AiMessageFeedbackRequest(
        /** 评分：1-5（1=很差，5=很好）；前端通常只传 1 或 5 对应踩/赞。 */
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最小为1")
        @Max(value = 5, message = "评分最大为5")
        Integer rating,
        /** 不满意的原因标签，如"答非所问"、"引用文档有误"等。 */
        String reason,
        /** 用户认为应该被引用的正确文档 ID，用于 RAG 召回质量分析和训练数据收集。 */
        Long expectedDocId,
        /** 用户详细的补充说明，自由文本。 */
        String comment
) {
}

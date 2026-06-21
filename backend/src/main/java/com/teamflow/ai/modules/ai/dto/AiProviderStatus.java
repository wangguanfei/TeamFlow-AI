package com.teamflow.ai.modules.ai.dto;

/**
 * AI Provider 状态 DTO，用于前端展示当前 AI 接入情况（GET /api/ai/provider/status）。
 * <p>
 * 前端据此决定是否展示 Mock 提示条，以及在设置页显示当前使用的模型和 Provider。
 */
public record AiProviderStatus(
        /** Provider 名称，如 deepseek、openai、mock 等。 */
        String provider,
        /** 当前使用的模型名称，Mock 模式下为 mock-ai。 */
        String model,
        /** 是否已配置真实 AI_API_KEY（apiKey 和 baseUrl 同时非空时为 true）。 */
        boolean configured,
        /** 是否正在使用 MockAiProvider（未配置或配置失效时为 true）。 */
        boolean mock
) {
}

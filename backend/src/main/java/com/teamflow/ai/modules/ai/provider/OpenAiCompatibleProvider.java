package com.teamflow.ai.modules.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * OpenAI Chat Completions 兼容供应商实现。
 *
 * <p>DeepSeek、OpenAI-compatible 网关以及很多国产模型代理都兼容 /chat/completions。
 * 本实现把普通聊天、流式聊天和 tool calling 都收敛到同一套请求格式；配置缺失或上游异常时
 * 统一回退到 MockAiProvider，保证演示环境和本地开发不会因为模型服务不可用而整体不可用。</p>
 */
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MockAiProvider mockAiProvider;

    public OpenAiCompatibleProvider(AiProperties properties, RestClient.Builder builder, ObjectMapper objectMapper,
                                    MockAiProvider mockAiProvider) {
        this.properties = properties;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.mockAiProvider = mockAiProvider;
        logProviderMode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiAnswer chat(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references, String model) {
        if (!configured()) {
            return mockAiProvider.chat(messages, mode, references);
        }
        String resolvedModel = resolveModel(model);
        Map<String, Object> request = buildRequestMap(messages, resolvedModel);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(chatCompletionUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> choices = response == null ? List.of() : (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
            if (choices.isEmpty()) {
                throw new IllegalStateException("AI 服务返回空结果");
            }
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.getOrDefault("message", Map.of());
            String content = String.valueOf(message.getOrDefault("content", ""));
            if (content.isBlank()) {
                throw new IllegalStateException("AI 服务返回空内容");
            }
            return new AiAnswer(content, Math.max(1, content.length() / 4), resolvedModel, false);
        } catch (Exception exception) {
            // 这里选择降级而不是向上抛出：AI 能力是增强功能，不能因为第三方模型波动拖垮核心业务演示。
            log.warn("AI 上游调用失败，已回退 MockAIProvider：{}", exception.getMessage());
            return mockAiProvider.chat(messages, mode, references);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiAgentAnswer chatWithTools(List<AiPromptMessage> messages, List<AiToolDefinition> tools, String model) {
        if (!configured()) {
            return mockAiProvider.chatWithTools(messages, tools);
        }
        String resolvedModel = resolveModel(model);
        Map<String, Object> request = buildRequestMap(messages, resolvedModel);
        if (tools != null && !tools.isEmpty()) {
            // OpenAI-compatible tool calling 要求 tools[*].function.parameters 是 JSON Schema。
            // 这些 schema 来自 AgentTool.definition()，新增工具时必须保证参数名与 execute() 解析一致。
            request.put("tools", tools.stream().map(this::toOpenAiTool).toList());
            request.put("tool_choice", "auto");
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(chatCompletionUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> choices = response == null ? List.of() : (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
            if (choices.isEmpty()) {
                throw new IllegalStateException("AI 服务返回空结果");
            }
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.getOrDefault("message", Map.of());
            String content = String.valueOf(message.getOrDefault("content", ""));
            List<AiToolCall> toolCalls = parseToolCalls((List<Map<String, Object>>) message.getOrDefault("tool_calls", List.of()));
            if (content.isBlank() && toolCalls.isEmpty()) {
                throw new IllegalStateException("AI 服务未返回内容或工具调用");
            }
            int tokens = Math.max(1, content.length() / 4);
            return new AiAgentAnswer(content, toolCalls, tokens, resolvedModel, false);
        } catch (Exception exception) {
            log.warn("AI Agent 上游调用失败，已回退 MockAIProvider：{}", exception.getMessage());
            return mockAiProvider.chatWithTools(messages, tools);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiAnswer chatStream(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references,
                               String model, Consumer<String> tokenConsumer) {
        if (!configured()) {
            AiAnswer answer = mockAiProvider.chat(messages, mode, references);
            tokenConsumer.accept(answer.content());
            return answer;
        }
        String resolvedModel = resolveModel(model);
        Map<String, Object> request = buildRequestMap(messages, resolvedModel);
        request.put("stream", true);

        StringBuilder fullContent = new StringBuilder();

        try {
            restClient.post()
                    .uri(chatCompletionUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, res) -> {
                        try (InputStream body = res.getBody();
                             BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) continue;
                                String data = line.substring(5).trim();
                                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                                try {
                                    // 上游 SSE 每一行都是一个 chat.completion.chunk；
                                    // 本方法只抽取 delta.content，并把最终完整文本拼回 AiAnswer 供落库。
                                    Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.getOrDefault("choices", List.of());
                                    if (!choices.isEmpty()) {
                                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).getOrDefault("delta", Map.of());
                                        String token = (String) delta.get("content");
                                        if (token != null && !token.isEmpty()) {
                                            fullContent.append(token);
                                            tokenConsumer.accept(token);
                                        }
                                    }
                                } catch (Exception ignored) {
                                    log.debug("Skip SSE chunk: {}", data);
                                }
                            }
                        }
                        return null;
                    });

            String content = fullContent.toString();
            if (content.isBlank()) {
                throw new IllegalStateException("AI 服务返回空内容");
            }
            return new AiAnswer(content, Math.max(1, content.length() / 4), resolvedModel, false);
        } catch (Exception exception) {
            log.warn("AI 流式上游调用失败，已回退 MockAIProvider：{}", exception.getMessage());
            AiAnswer answer = mockAiProvider.chat(messages, mode, references);
            tokenConsumer.accept(answer.content());
            return answer;
        }
    }

    private String resolveModel(String model) {
        return (model == null || model.isBlank()) ? properties.getModel() : model.trim();
    }

    /** 组装 OpenAI-compatible /chat/completions 请求体，temperature 固定 0.4（稳定性优先）。 */
    private Map<String, Object> buildRequestMap(List<AiPromptMessage> messages, String model) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("temperature", 0.4);
        // 内部角色统一使用大写，发给 OpenAI-compatible 上游前转为小写。
        request.put("messages", messages.stream()
                .map(message -> Map.of(
                        "role", normalizeRole(message.role()),
                        "content", message.content() == null ? "" : message.content()))
                .toList());
        return request;
    }

    private Map<String, Object> toOpenAiTool(AiToolDefinition tool) {
        Map<String, Object> function = new HashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.parameters());
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }

    /** 解析上游返回的 tool_calls 数组，arguments 字段支持 JSON 字符串或 Map 两种格式。 */
    @SuppressWarnings("unchecked")
    private List<AiToolCall> parseToolCalls(List<Map<String, Object>> rawCalls) {
        if (rawCalls == null || rawCalls.isEmpty()) {
            return List.of();
        }
        return rawCalls.stream()
                .map(call -> {
                    Map<String, Object> function = (Map<String, Object>) call.getOrDefault("function", Map.of());
                    String id = String.valueOf(call.getOrDefault("id", "call-" + UUID.randomUUID()));
                    String name = String.valueOf(function.getOrDefault("name", ""));
                    Object argumentsRaw = function.get("arguments");
                    Map<String, Object> arguments = parseArguments(argumentsRaw);
                    return new AiToolCall(id, name, arguments);
                })
                .filter(call -> call.name() != null && !call.name().isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                // OpenAI tool_call 的 arguments 通常是 JSON 字符串；部分兼容服务可能直接返回对象，
                // 所以上面先支持 Map，这里再解析字符串。
                return objectMapper.readValue(text, Map.class);
            } catch (Exception e) {
                log.warn("解析 tool_call arguments 失败：{}", e.getMessage());
                return Map.of();
            }
        }
        return Map.of();
    }

    private boolean configured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    private void logProviderMode() {
        if (configured()) {
            log.info("AI 提供商已初始化：provider={}, model={}, baseUrl={}", properties.getProvider(), properties.getModel(), properties.getBaseUrl());
        } else {
            log.info("AI_API_KEY 或 AI_BASE_URL 未配置，已启用 MockAIProvider");
        }
    }

    private String chatCompletionUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private String normalizeRole(String role) {
        if ("ASSISTANT".equalsIgnoreCase(role)) {
            return "assistant";
        }
        if ("SYSTEM".equalsIgnoreCase(role)) {
            return "system";
        }
        return "user";
    }
}

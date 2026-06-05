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
import java.util.function.Consumer;

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
            log.warn("AI 上游调用失败，已回退 MockAIProvider：{}", exception.getMessage());
            return mockAiProvider.chat(messages, mode, references);
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

    private Map<String, Object> buildRequestMap(List<AiPromptMessage> messages, String model) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("temperature", 0.4);
        request.put("messages", messages.stream()
                .map(message -> Map.of(
                        "role", normalizeRole(message.role()),
                        "content", message.content() == null ? "" : message.content()))
                .toList());
        return request;
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

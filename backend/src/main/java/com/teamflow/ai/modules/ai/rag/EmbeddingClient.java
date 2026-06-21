package com.teamflow.ai.modules.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 embedding HTTP 客户端。
 *
 * <p>Spring 后端不直接加载大模型，避免 JVM 进程被 PyTorch 依赖和内存峰值拖垮。
 * embedding-service 负责把文本编码成向量，本客户端只做输入截断、超时控制和失败降级。</p>
 */
@Service
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private final RagProperties properties;
    private final RestClient restClient;

    public EmbeddingClient(RagProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        Duration readTimeout = Duration.ofSeconds(properties.getEmbedding().getReadTimeoutSeconds());
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(readTimeout);
        this.restClient = builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }

    /**
     * 对文档内容进行向量化（写路径）。
     * 使用 "document" mode，bge 模型会在文本前加 "passage:" 前缀优化语义表示。
     * 返回空列表表示服务不可用，调用方应将该 chunk 标记为 FAILED 或跳过。
     */
    public List<Double> embedDocument(String text) {
        return embed(text, "document");
    }

    /**
     * 对用户查询语句进行向量化（读路径）。
     * 使用 "query" mode，bge 模型会加 "query:" 前缀，与 document 向量形成不对称匹配。
     * 返回空列表时调用方应自动降级为关键词检索。
     */
    public List<Double> embedQuery(String text) {
        return embed(text, "query");
    }

    @SuppressWarnings("unchecked")
    private List<Double> embed(String text, String mode) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        String safeText = limit(text);
        if (safeText.isBlank()) {
            return List.of();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("texts", List.of(safeText));
        request.put("mode", mode);
        request.put("model", properties.getEmbedding().getModel());
        try {
            // 只发单条文本，和 embedding-service 的 CPU-only 小机器部署保持一致。
            // 批量能力保留在协议里，后续升级硬件后可扩展。
            Map<String, Object> response = restClient.post()
                    .uri(endpoint("/embed"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            List<Object> vectors = response == null ? List.of() : (List<Object>) response.getOrDefault("vectors", List.of());
            if (vectors.isEmpty()) {
                return List.of();
            }
            List<Object> vector = (List<Object>) vectors.get(0);
            return vector.stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::doubleValue)
                    .toList();
        } catch (Exception exception) {
            // 检索路径会把空向量解释为 dense 召回不可用，然后自动走关键词兜底。
            log.warn("Embedding 服务调用失败 mode={} error={}", mode, exception.getMessage());
            return List.of();
        }
    }

    /** 探活 embedding-service，结果用于 GET /api/rag/status 健康检查。 */
    public boolean health() {
        try {
            restClient.get().uri(endpoint("/health")).retrieve().toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String limit(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        int maxChars = Math.max(200, properties.getEmbedding().getMaxTextChars());
        // 先在 Java 侧截断，避免异常长文档把 embedding 服务请求体和推理时间放大。
        return normalized.length() > maxChars ? normalized.substring(0, maxChars) : normalized;
    }

    private String endpoint(String path) {
        return properties.getEmbedding().getBaseUrl().replaceAll("/+$", "") + path;
    }
}

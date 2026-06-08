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

@Service
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    /** Embedding 为 CPU 推理，给较宽的读超时；但必须有上限，避免拖垮同步 chat 路径。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RagProperties properties;
    private final RestClient restClient;

    public EmbeddingClient(RagProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        this.restClient = builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }

    public List<Double> embedDocument(String text) {
        return embed(text, "document");
    }

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
            log.warn("Embedding 服务调用失败 mode={} error={}", mode, exception.getMessage());
            return List.of();
        }
    }

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
        return normalized.length() > maxChars ? normalized.substring(0, maxChars) : normalized;
    }

    private String endpoint(String path) {
        return properties.getEmbedding().getBaseUrl().replaceAll("/+$", "") + path;
    }
}

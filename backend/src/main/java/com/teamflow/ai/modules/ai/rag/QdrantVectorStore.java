package com.teamflow.ai.modules.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量库访问封装。
 *
 * <p>这个类只处理 collection 初始化、向量写入、检索和按文档删除。
 * 权限判断和关键词兜底在 AiKnowledgeIndexService 中完成；这里的失败会被吞掉并返回空结果，
 * 保证同步聊天接口不会因为向量库短暂异常直接失败。</p>
 */
@Service
public class QdrantVectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    /** Qdrant 调用必须有超时上限，避免向量检索卡死拖垮同步 chat 路径。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RagProperties properties;
    private final RestClient restClient;
    private volatile boolean collectionChecked;

    public QdrantVectorStore(RagProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        this.restClient = builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }

    /**
     * 确保 Qdrant collection 已存在（按需创建）。
     * 使用进程级 volatile flag 缓存，避免每次 upsert/search 都发一次 GET 探测。
     */
    public synchronized void ensureCollection() {
        if (collectionChecked) {
            return;
        }
        // collectionChecked 是进程级缓存，避免每个 chunk upsert/search 都先查一次 collection。
        // 若 Qdrant 重建 collection，重启后端即可重新探测。
        if (collectionExists()) {
            collectionChecked = true;
            return;
        }
        Map<String, Object> vectorParams = new LinkedHashMap<>();
        vectorParams.put("size", properties.getQdrant().getDimension());
        vectorParams.put("distance", "Cosine");
        Map<String, Object> body = Map.of("vectors", Map.of(properties.getQdrant().getVectorName(), vectorParams));
        restClient.put()
                .uri(endpoint("/collections/" + collection()))
                .headers(this::applyApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        collectionChecked = true;
        log.info("Qdrant 集合已初始化 collection={} vector={} dim={}",
                collection(), properties.getQdrant().getVectorName(), properties.getQdrant().getDimension());
    }

    /**
     * 写入或更新一个向量点（幂等 upsert）。
     * payload 中需包含 docId、spaceId、docStatus 等字段，检索命中时无需回表。
     * pointId 相同的 point 会被覆盖，保证重建幂等。
     */
    public void upsert(String pointId, List<Double> vector, Map<String, Object> payload) {
        if (pointId == null || pointId.isBlank() || vector == null || vector.isEmpty()) {
            return;
        }
        ensureCollection();
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointId);
        point.put("vector", Map.of(properties.getQdrant().getVectorName(), vector));
        // payload 放文档、空间、版本和 chunk 文本，检索命中后可直接组装引用卡片。
        point.put("payload", payload == null ? Map.of() : payload);
        restClient.put()
                .uri(endpoint("/collections/" + collection() + "/points?wait=true"))
                .headers(this::applyApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", List.of(point)))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 按向量相似度检索最相关的知识切片。
     * spaceId 不为 null 时只在指定空间内搜索，否则全局检索。
     * Qdrant 异常时返回空列表而不是抛出异常，上层降级到关键词检索。
     */
    @SuppressWarnings("unchecked")
    public List<QdrantSearchResult> search(List<Double> vector, Long spaceId, int limit) {
        if (vector == null || vector.isEmpty()) {
            return List.of();
        }
        try {
            // ensureCollection 也放进 try：Qdrant 不可用时建集合会抛异常，
            // 必须在此降级返回空结果，否则异常会击穿到同步 chat 路径（见类注释）。
            ensureCollection();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", Map.of("name", properties.getQdrant().getVectorName(), "vector", vector));
            body.put("filter", publishedFilter(spaceId));
            body.put("limit", Math.max(1, limit));
            body.put("with_payload", true);
            body.put("with_vector", false);
            Map<String, Object> response = restClient.post()
                    .uri(endpoint("/collections/" + collection() + "/points/search"))
                    .headers(this::applyApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<Object> raw = response == null ? List.of() : (List<Object>) response.getOrDefault("result", List.of());
            List<QdrantSearchResult> results = new ArrayList<>();
            for (Object item : raw) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String id = String.valueOf(map.get("id"));
                double score = map.get("score") instanceof Number number ? number.doubleValue() : 0.0;
                Map<String, Object> payload = map.get("payload") instanceof Map<?, ?> payloadMap
                        ? (Map<String, Object>) payloadMap
                        : Map.of();
                results.add(new QdrantSearchResult(id, score, payload));
            }
            return results;
        } catch (Exception exception) {
            log.warn("Qdrant 向量检索失败 collection={} error={}", collection(), exception.getMessage());
            return List.of();
        }
    }

    /** 按 docId payload 字段过滤删除该文档所有向量点（不分版本和 chunk）。 */
    public void deleteByDocId(Long docId) {
        if (docId == null) {
            return;
        }
        try {
            ensureCollection();
            restClient.post()
                    .uri(endpoint("/collections/" + collection() + "/points/delete?wait=true"))
                    .headers(this::applyApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("filter", matchFilter("docId", docId)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.warn("Qdrant 删除文档向量失败 docId={} error={}", docId, exception.getMessage());
        }
    }

    /** 检查 Qdrant 服务是否可用，用于 GET /api/rag/status 健康检查。 */
    public boolean health() {
        try {
            restClient.get()
                    .uri(endpoint("/"))
                    .headers(this::applyApiKey)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean collectionExists() {
        try {
            restClient.get()
                    .uri(endpoint("/collections/" + collection()))
                    .headers(this::applyApiKey)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> publishedFilter(Long spaceId) {
        List<Map<String, Object>> must = new ArrayList<>();
        // 删除、草稿或历史版本不参与问答召回；知识文档发布状态是 RAG 的硬边界。
        must.add(match("docStatus", "PUBLISHED"));
        if (spaceId != null) {
            must.add(match("spaceId", spaceId));
        }
        return Map.of("must", must);
    }

    private Map<String, Object> matchFilter(String key, Object value) {
        return Map.of("must", List.of(match(key, value)));
    }

    private Map<String, Object> match(String key, Object value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    private void applyApiKey(HttpHeaders headers) {
        String apiKey = properties.getQdrant().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("api-key", apiKey);
        }
    }

    private String endpoint(String path) {
        return properties.getQdrant().getBaseUrl().replaceAll("/+$", "") + path;
    }

    private String collection() {
        return properties.getQdrant().getCollection();
    }

    public record QdrantSearchResult(String pointId, double score, Map<String, Object> payload) {}
}

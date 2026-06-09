package com.teamflow.ai.modules.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * RAG 外部依赖（Embedding 服务 / Qdrant）不可用时的容错降级测试。
 *
 * <p>核心契约：检索路径上的调用在后端不可达时必须「软失败」——返回空结果 / false，而非抛异常，
 * 否则异常会击穿到同步 chat 路径。这里把 baseUrl 指向本机一个无服务端口（连接立即被拒绝），
 * 验证降级语义。
 */
class RagClientResilienceTest {

    /** 本机大概率无监听的端口，触发 connection refused，快速返回。 */
    private static final String DEAD_URL = "http://127.0.0.1:6";

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setBaseUrl(DEAD_URL);
        properties.getQdrant().setBaseUrl(DEAD_URL);
        return properties;
    }

    @Test
    @DisplayName("Embedding 服务不可用：embed 返回空、health 返回 false，不抛异常")
    void embeddingClient_degradesGracefully() {
        EmbeddingClient client = new EmbeddingClient(properties(), RestClient.builder());

        assertThatCode(() -> {
            assertThat(client.embedQuery("查询文本")).isEmpty();
            assertThat(client.embedDocument("文档文本")).isEmpty();
            assertThat(client.health()).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RAG 关闭时 embed 直接短路返回空，不发起网络调用")
    void embeddingClient_disabledShortCircuits() {
        RagProperties properties = properties();
        properties.setEnabled(false);
        EmbeddingClient client = new EmbeddingClient(properties, RestClient.builder());

        assertThat(client.embedQuery("查询文本")).isEmpty();
    }

    @Test
    @DisplayName("空白文本不发起 embed 调用，直接返回空")
    void embeddingClient_blankTextReturnsEmpty() {
        EmbeddingClient client = new EmbeddingClient(properties(), RestClient.builder());
        assertThat(client.embedQuery("   ")).isEmpty();
    }

    @Test
    @DisplayName("Qdrant 不可用：search 返回空、health 返回 false，不抛异常")
    void qdrantVectorStore_degradesGracefully() {
        QdrantVectorStore store = new QdrantVectorStore(properties(), RestClient.builder());

        assertThatCode(() -> {
            assertThat(store.search(List.of(0.1, 0.2, 0.3), 1L, 5)).isEmpty();
            assertThat(store.health()).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空向量直接返回空，不触发网络调用")
    void qdrantVectorStore_emptyVectorReturnsEmpty() {
        QdrantVectorStore store = new QdrantVectorStore(properties(), RestClient.builder());
        assertThat(store.search(List.of(), 1L, 5)).isEmpty();
    }

    @Test
    @DisplayName("deleteByDocId 在 Qdrant 不可用时静默降级，不抛异常")
    void qdrantVectorStore_deleteDegradesGracefully() {
        QdrantVectorStore store = new QdrantVectorStore(properties(), RestClient.builder());
        assertThatCode(() -> store.deleteByDocId(99L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("upsert 对空入参直接返回，不触发网络调用")
    void qdrantVectorStore_upsertNoopOnEmpty() {
        QdrantVectorStore store = new QdrantVectorStore(properties(), RestClient.builder());
        assertThatCode(() -> store.upsert("", List.of(), Map.of())).doesNotThrowAnyException();
    }
}

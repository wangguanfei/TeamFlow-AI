package com.teamflow.ai.modules.ai.service;

import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import com.teamflow.ai.modules.ai.rag.RagProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 核心纯算法单元测试：文本分块、分词、向量降级判定、point id 稳定性、摘要截取。
 *
 * <p>这些方法不触碰任何外部依赖（Qdrant/Embedding/MySQL/Redis），故服务以 null 依赖 + 默认
 * {@link RagProperties} 构造，通过反射直接验证算法本身。覆盖本次 RAG 优化的关键改动点。
 */
class AiKnowledgeIndexServiceTest {

    private final AiKnowledgeIndexService service = new AiKnowledgeIndexService(
            null, null, null, null, null, new RagProperties(), null, null, null);

    @SuppressWarnings("unchecked")
    private <T> T invoke(String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = AiKnowledgeIndexService.class.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(service, args);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    // ---------- 文本分块 ----------

    @Test
    @DisplayName("短文本不切分，返回单块")
    void splitChunks_shortText_singleChunk() {
        List<String> chunks = invoke("splitChunks", new Class<?>[]{String.class}, "一段很短的内容");
        assertThat(chunks).containsExactly("一段很短的内容");
    }

    @Test
    @DisplayName("空白/空串返回空列表")
    void splitChunks_blank_empty() {
        assertThat(this.<List<String>>invoke("splitChunks", new Class<?>[]{String.class}, "   ")).isEmpty();
        assertThat(this.<List<String>>invoke("splitChunks", new Class<?>[]{String.class}, (Object) null)).isEmpty();
    }

    @Test
    @DisplayName("超长文本按 720 切分并保留 120 字符重叠窗口")
    void splitChunks_longText_overlap() {
        // 用循环数字串，便于定位重叠区域（无空白，不受归一化影响）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            sb.append((char) ('0' + (i % 10)));
        }
        String text = sb.toString();

        List<String> chunks = invoke("splitChunks", new Class<?>[]{String.class}, text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(720);
        // 第二块从 720-120=600 开始，与第一块尾部 [600,720) 重叠
        String overlap = text.substring(600, 720);
        assertThat(chunks.get(0)).endsWith(overlap);
        assertThat(chunks.get(1)).startsWith(overlap);
    }

    // ---------- 分词 ----------

    @Test
    @DisplayName("分词同时产出英文词与中文单字，过滤单字母噪声")
    void tokenize_mixedLanguage() {
        List<String> tokens = invoke("tokenize", new Class<?>[]{String.class}, "Spring 框架 a 你好");
        assertThat(tokens)
                .contains("spring", "框架", "框", "架", "你好", "你", "好")
                .doesNotContain("a"); // 单个 ASCII 字母被过滤
    }

    @Test
    @DisplayName("分词最多保留 24 个 token")
    void tokenize_limited() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("词").append(i).append(' ');
        }
        List<String> tokens = invoke("tokenize", new Class<?>[]{String.class}, sb.toString());
        assertThat(tokens).hasSizeLessThanOrEqualTo(24);
    }

    // ---------- 向量召回降级判定（决定是否写缓存） ----------

    @Test
    @DisplayName("含 VECTOR/HYBRID 来源视为向量召回生效，可缓存")
    void hasDenseSource_trueForDense() {
        assertThat(this.<Boolean>invoke("hasDenseSource", new Class<?>[]{List.class},
                List.of(ref("VECTOR")))).isTrue();
        assertThat(this.<Boolean>invoke("hasDenseSource", new Class<?>[]{List.class},
                List.of(ref("KEYWORD"), ref("HYBRID")))).isTrue();
    }

    @Test
    @DisplayName("纯 KEYWORD 或空结果视为降级，不缓存")
    void hasDenseSource_falseForKeywordOnly() {
        assertThat(this.<Boolean>invoke("hasDenseSource", new Class<?>[]{List.class},
                List.of(ref("KEYWORD")))).isFalse();
        assertThat(this.<Boolean>invoke("hasDenseSource", new Class<?>[]{List.class},
                List.of())).isFalse();
    }

    // ---------- Qdrant point id 稳定性 ----------

    @Test
    @DisplayName("point id 对相同输入确定、对不同切片相异、且为合法 UUID")
    void pointId_deterministicAndDistinct() {
        Class<?>[] types = {Long.class, Integer.class, int.class, String.class};
        String a1 = invoke("pointId", types, 7L, 2, 0, "hash-abc");
        String a2 = invoke("pointId", types, 7L, 2, 0, "hash-abc");
        String b = invoke("pointId", types, 7L, 2, 1, "hash-abc");

        assertThat(a1).isEqualTo(a2);          // 幂等：可安全 upsert，不产生重复点
        assertThat(a1).isNotEqualTo(b);        // 不同 chunkIndex 必须不同点
        assertThat(java.util.UUID.fromString(a1)).isNotNull(); // 合法 UUID
    }

    // ---------- 摘要截取 ----------

    @Test
    @DisplayName("摘要以命中关键词为中心截取，两侧加省略号")
    void bestSnippet_centersOnHit() {
        String content = "前缀".repeat(60) + "目标关键词" + "后缀".repeat(60);
        String snippet = invoke("bestSnippet", new Class<?>[]{String.class, List.class, String.class},
                content, List.of("目标关键词"), "目标关键词");

        assertThat(snippet).contains("目标关键词");
        assertThat(snippet).startsWith("...").endsWith("...");
        assertThat(snippet.length()).isLessThan(content.length());
    }

    private AiReferenceItem ref(String source) {
        return new AiReferenceItem(1L, "标题", "片段", 1L, "空间", 1, 0, 1.0, "k", 0.5, 0.5, source);
    }

    // ---------- RRF 混合融合（本次优化核心） ----------

    @Test
    @DisplayName("dense+keyword 命中标记 HYBRID 且融合分最高，单路命中各自标记 VECTOR/KEYWORD 并按 RRF 排序")
    void fuseCandidates_rankAndSource() {
        // k1 同时命中 dense[0] 与 keyword[0] → HYBRID（融合分最高）
        // k2 仅命中 dense[1] → VECTOR（0.7/62）
        // k3 仅命中 keyword[1] → KEYWORD（0.3/62），应低于 k2
        Object k1Dense = candidate("k1", 10L, 0.9, null, "VECTOR");
        Object k2Dense = candidate("k2", 20L, 0.8, null, "VECTOR");
        Object k1Kw = candidate("k1", 10L, null, 5.0, "KEYWORD");
        Object k3Kw = candidate("k3", 30L, null, 4.0, "KEYWORD");

        List<AiReferenceItem> fused = invoke("fuseCandidates",
                new Class<?>[]{List.class, List.class, int.class},
                List.of(k1Dense, k2Dense), List.of(k1Kw, k3Kw), 5);

        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).chunkId()).isEqualTo("k1");
        assertThat(fused.get(0).retrievalSource()).isEqualTo("HYBRID");
        assertThat(fused).extracting(AiReferenceItem::chunkId)
                .containsExactly("k1", "k2", "k3"); // RRF: k1 > k2(dense) > k3(keyword)
        assertThat(fused.get(1).retrievalSource()).isEqualTo("VECTOR");
        assertThat(fused.get(2).retrievalSource()).isEqualTo("KEYWORD");
        assertThat(fused.get(0).score())
                .isGreaterThan(fused.get(1).score())
                .isGreaterThan(fused.get(2).score());
    }

    @Test
    @DisplayName("融合结果受 topK 截断")
    void fuseCandidates_respectsTopK() {
        List<AiReferenceItem> fused = invoke("fuseCandidates",
                new Class<?>[]{List.class, List.class, int.class},
                List.of(candidate("a", 1L, 0.9, null, "VECTOR"),
                        candidate("b", 2L, 0.8, null, "VECTOR"),
                        candidate("c", 3L, 0.7, null, "VECTOR")),
                List.of(), 2);
        assertThat(fused).hasSize(2);
    }

    /** 反射构造私有 record {@code ReferenceCandidate}（canonical 构造器 13 参）。 */
    private Object candidate(String key, Long docId, Double denseScore, Double keywordScore, String source) {
        try {
            Class<?> rc = Class.forName(
                    "com.teamflow.ai.modules.ai.service.AiKnowledgeIndexService$ReferenceCandidate");
            Constructor<?> ctor = rc.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(
                    key, docId, "标题" + key, "片段" + key, 1L, "空间", 1, 0,
                    0.0, denseScore, keywordScore, source, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException("构造 ReferenceCandidate 失败", e);
        }
    }
}

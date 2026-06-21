package com.teamflow.ai.modules.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 系统全量配置，从 application.yml（前缀 teamflow.rag）或环境变量读取。
 * <p>
 * 嵌套结构对应四个子系统：
 * <ul>
 *   <li>{@link Qdrant}：向量数据库连接配置（地址/集合/向量维度）</li>
 *   <li>{@link Embedding}：embedding-service 调用配置（地址/模型/超时）</li>
 *   <li>{@link Retrieval}：检索参数（各路召回 topK、RRF 融合权重）</li>
 *   <li>{@link Index}：后台索引 worker 调度参数（开关/重试/退避）</li>
 * </ul>
 * 对应环境变量详见 RAG_LIGHTWEIGHT_LOCAL.md 和 .env.example。
 */
@ConfigurationProperties(prefix = "teamflow.rag")
public class RagProperties {

    /** RAG 总开关；关闭后聊天仍可用，但只走普通模型或关键词兜底结果。 */
    private boolean enabled = true;
    /** true 表示当前后端会调用本机/内网 embedding 服务，需要启用内存保护。 */
    private boolean localEmbedding = true;
    /** 本地 embedding 推理前要求的最小 MemAvailable，单位 MB。 */
    private int minAvailableMemoryMb = 250;
    private Qdrant qdrant = new Qdrant();
    private Embedding embedding = new Embedding();
    private Retrieval retrieval = new Retrieval();
    private Index index = new Index();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isLocalEmbedding() { return localEmbedding; }
    public void setLocalEmbedding(boolean localEmbedding) { this.localEmbedding = localEmbedding; }
    public int getMinAvailableMemoryMb() { return minAvailableMemoryMb; }
    public void setMinAvailableMemoryMb(int minAvailableMemoryMb) { this.minAvailableMemoryMb = minAvailableMemoryMb; }
    public Qdrant getQdrant() { return qdrant; }
    public void setQdrant(Qdrant qdrant) { this.qdrant = qdrant; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Index getIndex() { return index; }
    public void setIndex(Index index) { this.index = index; }

    public static class Qdrant {
        /** Qdrant HTTP 地址，通常由 docker compose 内网或宿主机端口提供。 */
        private String baseUrl = "http://127.0.0.1:6333";
        private String apiKey = "";
        /** 存放知识库 chunk 向量的 collection 名称。 */
        private String collection = "teamflow_ai_knowledge_chunks";
        /** 命名向量字段，必须和创建 collection 时的 vectors 配置一致。 */
        private String vectorName = "dense";
        /** bge-small-zh-v1.5 输出 512 维，维度不一致会拒绝写入。 */
        private int dimension = 512;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public String getVectorName() { return vectorName; }
        public void setVectorName(String vectorName) { this.vectorName = vectorName; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
    }

    public static class Embedding {
        /** Python embedding-service 地址。 */
        private String baseUrl = "http://127.0.0.1:8000";
        /** 与 embedding-service 实际加载模型保持一致。 */
        private String model = "BAAI/bge-small-zh-v1.5";
        /** 单段文本最大字符数，Java 侧和 Python 侧都会再保护一次。 */
        private int maxTextChars = 4000;
        /** CPU-only 推理可能较慢，读超时需明显长于普通 HTTP 调用。 */
        private int readTimeoutSeconds = 30;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTextChars() { return maxTextChars; }
        public void setMaxTextChars(int maxTextChars) { this.maxTextChars = maxTextChars; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    }

    public static class Retrieval {
        /** 向量召回候选数，最终会和关键词候选融合后再裁剪。 */
        private int denseTopK = 20;
        /** 关键词召回候选数，作为向量故障和短 query 的兜底。 */
        private int keywordTopK = 20;
        /** 返回给模型和前端的最终引用条数。 */
        private int finalTopK = 5;
        /** 融合排序中向量召回权重。 */
        private double denseWeight = 0.7;
        /** 融合排序中关键词召回权重。 */
        private double keywordWeight = 0.3;

        public int getDenseTopK() { return denseTopK; }
        public void setDenseTopK(int denseTopK) { this.denseTopK = denseTopK; }
        public int getKeywordTopK() { return keywordTopK; }
        public void setKeywordTopK(int keywordTopK) { this.keywordTopK = keywordTopK; }
        public int getFinalTopK() { return finalTopK; }
        public void setFinalTopK(int finalTopK) { this.finalTopK = finalTopK; }
        public double getDenseWeight() { return denseWeight; }
        public void setDenseWeight(double denseWeight) { this.denseWeight = denseWeight; }
        public double getKeywordWeight() { return keywordWeight; }
        public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }
    }

    public static class Index {
        /** 后台索引 worker 开关；关闭后只入队不消费。 */
        private boolean workerEnabled = true;
        /** 单个索引任务最大尝试次数。 */
        private int maxAttempts = 3;
        /** worker 调度间隔，真实调度值来自 @Scheduled 配置。 */
        private long workerDelayMs = 5000L;
        /** FAILED 任务重试退避间隔：失败后需等待该时长才会被重新领取，避免瞬时抖动快速耗尽 attempts。 */
        private long retryBackoffMs = 30000L;

        public boolean isWorkerEnabled() { return workerEnabled; }
        public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getWorkerDelayMs() { return workerDelayMs; }
        public void setWorkerDelayMs(long workerDelayMs) { this.workerDelayMs = workerDelayMs; }
        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    }
}

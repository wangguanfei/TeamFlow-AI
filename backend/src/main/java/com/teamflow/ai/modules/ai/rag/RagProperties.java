package com.teamflow.ai.modules.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "teamflow.rag")
public class RagProperties {

    private boolean enabled = true;
    private boolean localEmbedding = true;
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
        private String baseUrl = "http://127.0.0.1:6333";
        private String apiKey = "";
        private String collection = "teamflow_ai_knowledge_chunks";
        private String vectorName = "dense";
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
        private String baseUrl = "http://127.0.0.1:8000";
        private String model = "BAAI/bge-small-zh-v1.5";
        private int maxTextChars = 4000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTextChars() { return maxTextChars; }
        public void setMaxTextChars(int maxTextChars) { this.maxTextChars = maxTextChars; }
    }

    public static class Retrieval {
        private int denseTopK = 20;
        private int keywordTopK = 20;
        private int finalTopK = 5;
        private double denseWeight = 0.7;
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
        private boolean workerEnabled = true;
        private int maxAttempts = 3;
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

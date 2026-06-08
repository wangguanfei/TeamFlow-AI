package com.teamflow.ai.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.teamflow.ai.common.cache.JsonCacheService;
import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import com.teamflow.ai.modules.ai.dto.RagStatus;
import com.teamflow.ai.modules.ai.entity.AiEmbedding;
import com.teamflow.ai.modules.ai.entity.AiIndexJob;
import com.teamflow.ai.modules.ai.mapper.AiEmbeddingMapper;
import com.teamflow.ai.modules.ai.mapper.AiIndexJobMapper;
import com.teamflow.ai.modules.ai.rag.EmbeddingClient;
import com.teamflow.ai.modules.ai.rag.QdrantVectorStore;
import com.teamflow.ai.modules.ai.rag.RagProperties;
import com.teamflow.ai.modules.ai.rag.RagResourceGuardService;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeDoc;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeDocMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeSpaceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiKnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeIndexService.class);

    private static final int CHUNK_SIZE = 720;
    private static final int CHUNK_OVERLAP = 120;
    private static final String ACTION_REBUILD = "REBUILD";
    private static final String ACTION_DELETE = "DELETE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    /** RAG 检索结果缓存：按 query+space+topK 缓存；索引变化时统一失效。 */
    private static final String RAG_KEY_PREFIX = "ai:rag:";
    private static final Duration RAG_TTL = Duration.ofMinutes(10);

    private final AiEmbeddingMapper embeddingMapper;
    private final AiIndexJobMapper indexJobMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final JsonCacheService jsonCacheService;
    private final RagProperties ragProperties;
    private final RagResourceGuardService resourceGuardService;
    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore qdrantVectorStore;

    public AiKnowledgeIndexService(
            AiEmbeddingMapper embeddingMapper,
            AiIndexJobMapper indexJobMapper,
            KnowledgeDocMapper docMapper,
            KnowledgeSpaceMapper spaceMapper,
            JsonCacheService jsonCacheService,
            RagProperties ragProperties,
            RagResourceGuardService resourceGuardService,
            EmbeddingClient embeddingClient,
            QdrantVectorStore qdrantVectorStore
    ) {
        this.embeddingMapper = embeddingMapper;
        this.indexJobMapper = indexJobMapper;
        this.docMapper = docMapper;
        this.spaceMapper = spaceMapper;
        this.jsonCacheService = jsonCacheService;
        this.ragProperties = ragProperties;
        this.resourceGuardService = resourceGuardService;
        this.embeddingClient = embeddingClient;
        this.qdrantVectorStore = qdrantVectorStore;
    }

    @Transactional
    public void rebuildDocumentIndex(KnowledgeDoc doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        enqueueIndexJob(doc.getId(), ACTION_REBUILD);
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        log.info("已提交文档RAG索引重建任务 docId={} versionNo={}", doc.getId(), doc.getVersionNo());
    }

    @Transactional
    public void deleteDocumentIndex(Long docId) {
        if (docId == null) {
            return;
        }
        embeddingMapper.delete(new LambdaQueryWrapper<AiEmbedding>().eq(AiEmbedding::getDocId, docId));
        qdrantVectorStore.deleteByDocId(docId);
        enqueueIndexJob(docId, ACTION_DELETE);
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        log.info("已提交文档RAG索引删除任务 docId={}", docId);
    }

    // 不加 @Transactional：本方法是检索路径，内部调用 embedding/Qdrant 网络 IO，
    // 若包在事务里会在网络调用期间长时间占用 DB 连接（叠加 MySQL max-connections 限制易耗尽连接池）。
    // 缺失索引补建走的是各自独立提交的 enqueueIndexJob，无需事务原子性。
    public List<AiReferenceItem> searchReferences(String query, Long spaceId, int topK) {
        String safeQuery = normalize(query);
        int finalTopK = topK > 0 ? topK : ragProperties.getRetrieval().getFinalTopK();
        String cacheKey = RAG_KEY_PREFIX
                + (ragProperties.isEnabled() ? "hybrid" : "keyword") + ":"
                + (spaceId == null ? "all" : spaceId) + ":"
                + Math.max(1, finalTopK) + ":"
                + sha256(safeQuery);
        boolean ragEnabled = ragProperties.isEnabled();
        return jsonCacheService.getOrLoad(cacheKey, RAG_TTL, new TypeReference<List<AiReferenceItem>>() {},
                () -> ragEnabled
                        ? hybridSearch(safeQuery, spaceId, finalTopK)
                        : keywordCandidates(safeQuery, spaceId, finalTopK).stream().map(ReferenceCandidate::toReference).toList(),
                // RAG 关闭时关键词结果可正常缓存；RAG 启用时仅当向量召回真正生效（结果含 VECTOR/HYBRID 来源）
                // 才缓存——否则视为 embedding/Qdrant 故障降级，不缓存以免恢复后仍在 TTL 内返回降级结果。
                results -> !ragEnabled || hasDenseSource(results));
    }

    private boolean hasDenseSource(List<AiReferenceItem> results) {
        return results.stream().anyMatch(item ->
                "VECTOR".equals(item.retrievalSource()) || "HYBRID".equals(item.retrievalSource()));
    }

    public long enqueueRebuild(Long docId) {
        if (docId == null) {
            return 0;
        }
        enqueueIndexJob(docId, ACTION_REBUILD);
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        return 1;
    }

    public long enqueueRebuildSpace(Long spaceId) {
        List<KnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0)
                .eq(KnowledgeDoc::getDocStatus, "PUBLISHED")
                .eq(spaceId != null, KnowledgeDoc::getSpaceId, spaceId)
                .orderByAsc(KnowledgeDoc::getId));
        docs.forEach(doc -> enqueueIndexJob(doc.getId(), ACTION_REBUILD));
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        return docs.size();
    }

    public RagStatus ragStatus() {
        long pending = countJobs(STATUS_PENDING);
        long running = countJobs(STATUS_RUNNING);
        long failed = countJobs(STATUS_FAILED);
        boolean memoryOk = resourceGuardService.localEmbeddingAllowed();
        return new RagStatus(
                ragProperties.isEnabled(),
                ragProperties.getIndex().isWorkerEnabled(),
                qdrantVectorStore.health(),
                embeddingClient.health(),
                memoryOk,
                resourceGuardService.memAvailableMb(),
                pending,
                running,
                failed,
                ragProperties.getQdrant().getCollection(),
                ragProperties.getEmbedding().getModel()
        );
    }

    @Scheduled(fixedDelayString = "${teamflow.rag.index.worker-delay-ms:5000}")
    public void processNextIndexJob() {
        if (!ragProperties.isEnabled() || !ragProperties.getIndex().isWorkerEnabled()) {
            return;
        }
        AiIndexJob job = nextRunnableJob();
        if (job == null) {
            return;
        }
        claimJob(job);
        try {
            if (ACTION_DELETE.equals(job.getActionType())) {
                processDelete(job.getDocId());
            } else {
                processRebuild(job.getDocId());
            }
            finishJob(job, STATUS_DONE, null);
        } catch (Exception exception) {
            log.warn("RAG索引任务失败 jobId={} docId={} action={} error={}",
                    job.getId(), job.getDocId(), job.getActionType(), exception.getMessage());
            finishJob(job, STATUS_FAILED, exception.getMessage());
        }
    }

    private List<AiReferenceItem> hybridSearch(String safeQuery, Long spaceId, int topK) {
        List<ReferenceCandidate> keywordCandidates = keywordCandidates(
                safeQuery, spaceId, ragProperties.getRetrieval().getKeywordTopK());
        List<ReferenceCandidate> denseCandidates = denseCandidates(
                safeQuery, spaceId, ragProperties.getRetrieval().getDenseTopK());
        List<AiReferenceItem> fused = fuseCandidates(denseCandidates, keywordCandidates, topK);
        if (!fused.isEmpty()) {
            return fused;
        }
        return keywordCandidates.stream().limit(Math.max(1, topK)).map(ReferenceCandidate::toReference).toList();
    }

    private List<ReferenceCandidate> denseCandidates(String safeQuery, Long spaceId, int topK) {
        if (safeQuery.isBlank() || !resourceGuardService.localEmbeddingAllowed()) {
            return List.of();
        }
        List<Double> queryVector = embeddingClient.embedQuery(safeQuery);
        if (queryVector.isEmpty()) {
            return List.of();
        }
        return qdrantVectorStore.search(queryVector, spaceId, Math.max(1, topK))
                .stream()
                .map(result -> candidateFromPayload(result.pointId(), result.score(), result.payload(), safeQuery))
                .filter(Objects::nonNull)
                .toList();
    }

    private ReferenceCandidate candidateFromPayload(
            String pointId,
            double denseScore,
            Map<String, Object> payload,
            String query
    ) {
        Long docId = asLong(payload.get("docId"));
        if (docId == null) {
            return null;
        }
        String chunk = stringValue(payload.get("chunkText"));
        String title = stringValue(payload.get("title"));
        Long spaceId = asLong(payload.get("spaceId"));
        String spaceName = stringValue(payload.get("spaceName"));
        Integer versionNo = asInteger(payload.get("versionNo"));
        Integer chunkIndex = asInteger(payload.get("chunkIndex"));
        List<String> queryTokens = tokenize(query);
        return new ReferenceCandidate(
                pointId == null || pointId.isBlank() ? docId + ":" + chunkIndex : pointId,
                docId,
                title,
                bestSnippet(chunk, queryTokens, query),
                spaceId,
                spaceName,
                versionNo,
                chunkIndex,
                denseScore,
                denseScore,
                null,
                "VECTOR",
                null
        );
    }

    private List<ReferenceCandidate> keywordCandidates(String safeQuery, Long spaceId, int topK) {
        List<KnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0)
                .eq(KnowledgeDoc::getDocStatus, "PUBLISHED")
                .eq(spaceId != null, KnowledgeDoc::getSpaceId, spaceId)
                .orderByDesc(KnowledgeDoc::getUpdatedAt)
                .last("LIMIT 80"));
        if (docs.isEmpty()) {
            return List.of();
        }
        enqueueMissingIndexes(docs);
        log.debug("RAG关键词检索 spaceId={} topK={} 候选文档数={}", spaceId, topK, docs.size());
        Map<Long, KnowledgeDoc> docMap = docs.stream().collect(Collectors.toMap(KnowledgeDoc::getId, Function.identity()));
        Map<Long, KnowledgeSpace> spaceMap = loadSpaces(docs);
        List<AiEmbedding> embeddings = embeddingMapper.selectList(new LambdaQueryWrapper<AiEmbedding>()
                .in(AiEmbedding::getDocId, docMap.keySet())
                .orderByAsc(AiEmbedding::getDocId)
                .orderByAsc(AiEmbedding::getChunkIndex));
        if (embeddings.isEmpty()) {
            return fallbackDocs(docs, spaceMap, safeQuery, topK);
        }
        List<String> queryTokens = tokenize(safeQuery);
        return embeddings.stream()
                .map(embedding -> scoreEmbedding(embedding, docMap.get(embedding.getDocId()), spaceMap, safeQuery, queryTokens))
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.score() > 0 || safeQuery.isBlank())
                .sorted(Comparator.comparingDouble(ReferenceCandidate::score).reversed()
                        .thenComparing(ReferenceCandidate::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, topK))
                .toList();
    }

    private List<AiReferenceItem> fuseCandidates(
            List<ReferenceCandidate> denseCandidates,
            List<ReferenceCandidate> keywordCandidates,
            int topK
    ) {
        Map<String, FusionCandidate> fused = new LinkedHashMap<>();
        double denseWeight = ragProperties.getRetrieval().getDenseWeight();
        double keywordWeight = ragProperties.getRetrieval().getKeywordWeight();
        for (int i = 0; i < denseCandidates.size(); i++) {
            ReferenceCandidate candidate = denseCandidates.get(i);
            FusionCandidate fusion = fused.computeIfAbsent(candidate.key(), key -> new FusionCandidate(candidate));
            fusion.addDense(candidate, denseWeight / (60.0 + i + 1));
        }
        for (int i = 0; i < keywordCandidates.size(); i++) {
            ReferenceCandidate candidate = keywordCandidates.get(i);
            FusionCandidate fusion = fused.computeIfAbsent(candidate.key(), key -> new FusionCandidate(candidate));
            fusion.addKeyword(candidate, keywordWeight / (60.0 + i + 1));
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(FusionCandidate::fusionScore).reversed())
                .limit(Math.max(1, topK))
                .map(FusionCandidate::toReference)
                .toList();
    }

    private void enqueueMissingIndexes(List<KnowledgeDoc> docs) {
        List<Long> docIds = docs.stream().map(KnowledgeDoc::getId).filter(Objects::nonNull).toList();
        if (docIds.isEmpty()) {
            return;
        }
        Set<Long> indexedDocIds = embeddingMapper.selectList(new LambdaQueryWrapper<AiEmbedding>()
                        .in(AiEmbedding::getDocId, docIds))
                .stream()
                .map(AiEmbedding::getDocId)
                .collect(Collectors.toSet());
        docs.stream()
                .filter(doc -> !indexedDocIds.contains(doc.getId()))
                .forEach(doc -> enqueueIndexJob(doc.getId(), ACTION_REBUILD));
    }

    private ReferenceCandidate scoreEmbedding(
            AiEmbedding embedding,
            KnowledgeDoc doc,
            Map<Long, KnowledgeSpace> spaceMap,
            String query,
            List<String> queryTokens
    ) {
        if (doc == null) {
            return null;
        }
        String title = normalize(doc.getTitle());
        String chunk = normalize(embedding.getChunkText());
        String fullText = normalize(indexableText(doc));
        double score = 0;
        if (!query.isBlank()) {
            if (title.contains(query)) score += 14;
            if (chunk.contains(query)) score += 18;
            if (fullText.contains(query)) score += 6;
        }
        for (String token : queryTokens) {
            if (title.contains(token)) score += 4;
            if (chunk.contains(token)) score += 3;
            if (fullText.contains(token)) score += 1;
        }
        if ("PUBLISHED".equalsIgnoreCase(doc.getDocStatus())) {
            score += 1.2;
        }
        if (doc.getVersionNo() != null) {
            score += Math.min(2, doc.getVersionNo() * 0.2);
        }
        String snippet = bestSnippet(embedding.getChunkText(), queryTokens, query);
        KnowledgeSpace space = spaceMap.get(doc.getSpaceId());
        return new ReferenceCandidate(
                keyOf(embedding),
                doc.getId(),
                doc.getTitle(),
                snippet,
                doc.getSpaceId(),
                space == null ? null : space.getSpaceName(),
                doc.getVersionNo(),
                embedding.getChunkIndex(),
                round(score),
                null,
                round(score),
                "KEYWORD",
                doc.getUpdatedAt()
        );
    }

    private List<ReferenceCandidate> fallbackDocs(List<KnowledgeDoc> docs, Map<Long, KnowledgeSpace> spaceMap, String query, int topK) {
        List<String> tokens = tokenize(query);
        return docs.stream()
                .limit(Math.max(1, topK))
                .map(doc -> {
                    KnowledgeSpace space = spaceMap.get(doc.getSpaceId());
                    return new ReferenceCandidate(
                            "doc:" + doc.getId(),
                            doc.getId(),
                            doc.getTitle(),
                            bestSnippet(indexableText(doc), tokens, query),
                            doc.getSpaceId(),
                            space == null ? null : space.getSpaceName(),
                            doc.getVersionNo(),
                            0,
                            0.0,
                            null,
                            0.0,
                            "KEYWORD",
                            doc.getUpdatedAt()
                    );
                })
                .toList();
    }

    private void processRebuild(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        processDelete(docId);
        if (doc == null || doc.getDeleted() != null && doc.getDeleted() == 1
                || !"PUBLISHED".equalsIgnoreCase(doc.getDocStatus())) {
            return;
        }
        if (!resourceGuardService.localEmbeddingAllowed()) {
            throw new IllegalStateException("服务器可用内存低于RAG本地Embedding门槛: "
                    + resourceGuardService.memAvailableMb() + "MB");
        }
        String text = indexableText(doc);
        if (text.isBlank()) {
            return;
        }
        KnowledgeSpace space = doc.getSpaceId() == null ? null : spaceMapper.selectById(doc.getSpaceId());
        List<String> chunks = splitChunks(text);
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            String contentHash = sha256(doc.getId() + ":" + doc.getVersionNo() + ":" + index + ":" + chunk);
            String pointId = pointId(doc.getId(), doc.getVersionNo(), index, contentHash);
            AiEmbedding embedding = new AiEmbedding();
            embedding.setDocId(doc.getId());
            embedding.setChunkIndex(index);
            embedding.setChunkText(chunk);
            embedding.setEmbeddingHash(contentHash);
            embedding.setEmbeddingText(null);
            embedding.setEmbeddingModel(ragProperties.getEmbedding().getModel());
            embedding.setEmbeddingDim(ragProperties.getQdrant().getDimension());
            embedding.setVectorPointId(pointId);
            embedding.setContentHash(contentHash);
            embedding.setIndexStatus(STATUS_PENDING);
            embedding.setCreatedAt(now);
            embedding.setUpdatedAt(now);
            embeddingMapper.insert(embedding);

            List<Double> vector = embeddingClient.embedDocument(chunk);
            if (vector.size() != ragProperties.getQdrant().getDimension()) {
                embedding.setIndexStatus(STATUS_FAILED);
                embedding.setIndexError("Embedding维度不匹配: " + vector.size());
                embedding.setUpdatedAt(LocalDateTime.now());
                embeddingMapper.updateById(embedding);
                throw new IllegalStateException("Embedding维度不匹配: " + vector.size());
            }
            qdrantVectorStore.upsert(pointId, vector, payload(doc, space, embedding, contentHash));
            embedding.setIndexStatus("READY");
            embedding.setIndexedAt(LocalDateTime.now());
            embedding.setUpdatedAt(LocalDateTime.now());
            embeddingMapper.updateById(embedding);
        }
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        log.info("文档RAG索引重建完成 docId={} versionNo={} chunks={}", doc.getId(), doc.getVersionNo(), chunks.size());
    }

    private void processDelete(Long docId) {
        if (docId == null) {
            return;
        }
        embeddingMapper.delete(new LambdaQueryWrapper<AiEmbedding>().eq(AiEmbedding::getDocId, docId));
        qdrantVectorStore.deleteByDocId(docId);
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
    }

    private Map<String, Object> payload(
            KnowledgeDoc doc,
            KnowledgeSpace space,
            AiEmbedding embedding,
            String contentHash
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", doc.getId());
        payload.put("title", doc.getTitle());
        payload.put("spaceId", doc.getSpaceId());
        payload.put("spaceName", space == null ? null : space.getSpaceName());
        payload.put("versionNo", doc.getVersionNo());
        payload.put("chunkIndex", embedding.getChunkIndex());
        payload.put("chunkText", embedding.getChunkText());
        payload.put("docStatus", doc.getDocStatus());
        payload.put("contentHash", contentHash);
        payload.put("updatedAt", doc.getUpdatedAt() == null ? null : doc.getUpdatedAt().toString());
        return payload;
    }

    private Map<Long, KnowledgeSpace> loadSpaces(List<KnowledgeDoc> docs) {
        List<Long> spaceIds = docs.stream().map(KnowledgeDoc::getSpaceId).filter(Objects::nonNull).distinct().toList();
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        return spaceMapper.selectBatchIds(spaceIds)
                .stream()
                .collect(Collectors.toMap(KnowledgeSpace::getId, Function.identity(), (left, right) -> left));
    }

    private List<String> splitChunks(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            chunks.add(normalized.substring(start, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private String bestSnippet(String content, List<String> tokens, String query) {
        String text = normalizeWhitespace(content);
        if (text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int hit = query == null || query.isBlank() ? -1 : lower.indexOf(query.toLowerCase(Locale.ROOT));
        if (hit < 0) {
            for (String token : tokens) {
                hit = lower.indexOf(token.toLowerCase(Locale.ROOT));
                if (hit >= 0) {
                    break;
                }
            }
        }
        int start = hit < 0 ? 0 : Math.max(0, hit - 70);
        int end = Math.min(text.length(), start + 180);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < text.length() ? "..." : "";
        return prefix + text.substring(start, end).trim() + suffix;
    }

    private List<String> tokenize(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
            for (int i = 0; i < part.length(); i++) {
                char ch = part.charAt(i);
                if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                    tokens.add(String.valueOf(ch));
                }
            }
        }
        return tokens.stream()
                .filter(token -> token.length() > 1 || token.matches("[\\u4e00-\\u9fa5]"))
                .limit(24)
                .toList();
    }

    private String indexableText(KnowledgeDoc doc) {
        String content = doc.getContentText() == null || doc.getContentText().isBlank()
                ? markdownToText(doc.getContentMd())
                : doc.getContentText();
        String title = normalizeWhitespace(doc.getTitle());
        String body = normalizeWhitespace(content);
        if (title.isBlank() || body.toLowerCase(Locale.ROOT).startsWith(title.toLowerCase(Locale.ROOT))) {
            return body;
        }
        return normalizeWhitespace(title + "\n" + body);
    }

    private String markdownToText(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("[#>*_\\-]+", " ");
    }

    private void enqueueIndexJob(Long docId, String action) {
        if (docId == null) {
            return;
        }
        AiIndexJob job = new AiIndexJob();
        job.setDocId(docId);
        job.setActionType(action);
        job.setJobStatus(STATUS_PENDING);
        job.setAttempts(0);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        indexJobMapper.insert(job);
    }

    private AiIndexJob nextRunnableJob() {
        LocalDateTime retryThreshold = LocalDateTime.now()
                .minus(Duration.ofMillis(ragProperties.getIndex().getRetryBackoffMs()));
        // PENDING 立即可领；FAILED 需等过退避窗口（updatedAt 早于阈值）才重试，避免瞬时抖动快速耗尽 attempts。
        return indexJobMapper.selectOne(new LambdaQueryWrapper<AiIndexJob>()
                .lt(AiIndexJob::getAttempts, ragProperties.getIndex().getMaxAttempts())
                .and(wrapper -> wrapper
                        .eq(AiIndexJob::getJobStatus, STATUS_PENDING)
                        .or(sub -> sub.eq(AiIndexJob::getJobStatus, STATUS_FAILED)
                                .lt(AiIndexJob::getUpdatedAt, retryThreshold)))
                .orderByAsc(AiIndexJob::getId)
                .last("LIMIT 1"));
    }

    private void claimJob(AiIndexJob job) {
        job.setJobStatus(STATUS_RUNNING);
        job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
        job.setLockedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        indexJobMapper.updateById(job);
    }

    private void finishJob(AiIndexJob job, String status, String errorMessage) {
        job.setJobStatus(status);
        job.setErrorMessage(errorMessage == null ? null : truncate(errorMessage, 1000));
        job.setUpdatedAt(LocalDateTime.now());
        indexJobMapper.updateById(job);
    }

    private long countJobs(String status) {
        return indexJobMapper.selectCount(new LambdaQueryWrapper<AiIndexJob>().eq(AiIndexJob::getJobStatus, status));
    }

    private String keyOf(AiEmbedding embedding) {
        if (embedding.getVectorPointId() != null && !embedding.getVectorPointId().isBlank()) {
            return embedding.getVectorPointId();
        }
        return embedding.getDocId() + ":" + embedding.getChunkIndex();
    }

    private String pointId(Long docId, Integer versionNo, int chunkIndex, String contentHash) {
        String source = "teamflow:" + docId + ":" + versionNo + ":" + chunkIndex + ":" + contentHash;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(String value) {
        return normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private double round(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            return "demo-hash";
        }
    }

    private record ReferenceCandidate(
            String key,
            Long docId,
            String title,
            String snippet,
            Long spaceId,
            String spaceName,
            Integer versionNo,
            Integer chunkIndex,
            Double score,
            Double denseScore,
            Double keywordScore,
            String retrievalSource,
            LocalDateTime updatedAt
    ) {
        AiReferenceItem toReference() {
            return new AiReferenceItem(
                    docId,
                    title,
                    snippet,
                    spaceId,
                    spaceName,
                    versionNo,
                    chunkIndex,
                    score,
                    key,
                    denseScore,
                    keywordScore,
                    retrievalSource
            );
        }
    }

    private static class FusionCandidate {
        private ReferenceCandidate candidate;
        private double fusionScore;
        private Double denseScore;
        private Double keywordScore;

        FusionCandidate(ReferenceCandidate candidate) {
            this.candidate = candidate;
        }

        void addDense(ReferenceCandidate candidate, double value) {
            this.candidate = candidate;
            this.fusionScore += value;
            this.denseScore = candidate.denseScore();
        }

        void addKeyword(ReferenceCandidate candidate, double value) {
            if (this.candidate == null || this.denseScore == null) {
                this.candidate = candidate;
            }
            this.fusionScore += value;
            this.keywordScore = candidate.keywordScore();
        }

        double fusionScore() {
            return fusionScore;
        }

        AiReferenceItem toReference() {
            String source = denseScore != null && keywordScore != null
                    ? "HYBRID"
                    : denseScore != null ? "VECTOR" : "KEYWORD";
            return new AiReferenceItem(
                    candidate.docId(),
                    candidate.title(),
                    candidate.snippet(),
                    candidate.spaceId(),
                    candidate.spaceName(),
                    candidate.versionNo(),
                    candidate.chunkIndex(),
                    Math.round(fusionScore * 10000.0) / 10.0,
                    candidate.key(),
                    denseScore,
                    keywordScore,
                    source
            );
        }
    }
}

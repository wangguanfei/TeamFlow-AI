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
import com.teamflow.ai.modules.ai.rag.RagSearchScope;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeDoc;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeDocMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeSpaceMapper;
import com.teamflow.ai.modules.system.service.PermissionQueryService;
import com.teamflow.ai.modules.team.entity.Team;
import com.teamflow.ai.modules.team.entity.TeamMember;
import com.teamflow.ai.modules.team.mapper.TeamMapper;
import com.teamflow.ai.modules.team.mapper.TeamMemberMapper;
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

/**
 * 知识库 RAG 索引与检索服务。
 *
 * <p>这里同时承担两条链路：</p>
 * <p>1. 写链路：知识文档发布/删除后写入 ai_index_job，后台 worker 切块、调用本地
 * embedding 服务、把向量和 payload 写入 Qdrant，并在 MySQL 的 ai_embedding 表保留切片元数据。</p>
 * <p>2. 读链路：聊天时先按用户权限收敛可见知识空间，再做关键词召回和向量召回，
 * 用 RRF 风格的排序融合返回引用片段。Qdrant 或 embedding 不可用时降级关键词召回，
 * 但不会把降级结果缓存成“混合检索成功”。</p>
 */
@Service
public class AiKnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeIndexService.class);

    /** 以中文知识文档为主，720 字符能兼顾上下文完整度和 bge-small 的输入长度成本。 */
    private static final int CHUNK_SIZE = 720;
    /** 相邻切片保留重叠，避免答案刚好跨切片边界时召回不到完整上下文。 */
    private static final int CHUNK_OVERLAP = 120;
    private static final String ACTION_REBUILD = "REBUILD";
    private static final String ACTION_DELETE = "DELETE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    /** RAG 检索结果缓存：按 query+space+topK 缓存；索引变化时统一失效。 */
    private static final String RAG_KEY_PREFIX = "ai:rag:";
    private static final Duration RAG_TTL = Duration.ofMinutes(10);

    private final AiEmbeddingMapper embeddingMapper;
    private final AiIndexJobMapper indexJobMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final PermissionQueryService permissionQueryService;
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
            TeamMapper teamMapper,
            TeamMemberMapper teamMemberMapper,
            PermissionQueryService permissionQueryService,
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
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.permissionQueryService = permissionQueryService;
        this.jsonCacheService = jsonCacheService;
        this.ragProperties = ragProperties;
        this.resourceGuardService = resourceGuardService;
        this.embeddingClient = embeddingClient;
        this.qdrantVectorStore = qdrantVectorStore;
    }

    /**
     * 文档发布时触发索引重建（写链路入口）。
     * 只负责入队 REBUILD 任务并清缓存，实际切块/向量化由后台 worker 异步完成。
     * 调用方是 KnowledgeService.publishDocument()。
     */
    @Transactional
    public void rebuildDocumentIndex(KnowledgeDoc doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        enqueueIndexJob(doc.getId(), ACTION_REBUILD);
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        log.info("已提交文档RAG索引重建任务 docId={} versionNo={}", doc.getId(), doc.getVersionNo());
    }

    /**
     * 文档删除/下线时清除向量索引。
     * 同步删除 MySQL ai_embedding 记录和 Qdrant 向量点，再入队 DELETE 审计 job。
     * 调用方是 KnowledgeService.deleteDoc()。
     */
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
    /**
     * 匿名检索入口（无权限过滤），委托给带 userId 的重载。
     * 适用于内部测试或不需要权限隔离的调用场景。
     */
    public List<AiReferenceItem> searchReferences(String query, Long spaceId, int topK) {
        return searchReferences(query, spaceId, topK, null);
    }

    /**
     * RAG 知识检索主入口（读链路）。
     * <p>
     * 流程：权限范围收敛 → 缓存命中检查 → 混合检索（向量 + 关键词 + RRF 融合）。
     * 缓存 key 包含用户权限范围和 query hash，保证不同用户看到各自可见的结果。
     * 仅当向量召回真正生效时才缓存，embedding/Qdrant 故障降级的结果不缓存。
     */
    public List<AiReferenceItem> searchReferences(String query, Long spaceId, int topK, Long userId) {
        String safeQuery = normalize(query);
        int finalTopK = topK > 0 ? topK : ragProperties.getRetrieval().getFinalTopK();
        RagSearchScope scope = searchScope(userId);
        // 缓存 key 必须包含检索模式、用户可见范围、空间和 query hash。
        // 否则不同用户/不同知识空间可能共享到不该看的引用结果。
        String cacheKey = RAG_KEY_PREFIX
                + (ragProperties.isEnabled() ? "hybrid" : "keyword") + ":"
                + scope.cacheKey() + ":"
                + (spaceId == null ? "all" : spaceId) + ":"
                + Math.max(1, finalTopK) + ":"
                + sha256(safeQuery);
        boolean ragEnabled = ragProperties.isEnabled();
        return jsonCacheService.getOrLoad(cacheKey, RAG_TTL, new TypeReference<List<AiReferenceItem>>() {},
                () -> ragEnabled
                        ? hybridSearch(safeQuery, spaceId, finalTopK, scope)
                        : keywordCandidates(safeQuery, finalTopK, visibleSpaceIds(scope, spaceId)).stream().map(ReferenceCandidate::toReference).toList(),
                // RAG 关闭时关键词结果可正常缓存；RAG 启用时仅当向量召回真正生效（结果含 VECTOR/HYBRID 来源）
                // 才缓存——否则视为 embedding/Qdrant 故障降级，不缓存以免恢复后仍在 TTL 内返回降级结果。
                results -> !ragEnabled || hasDenseSource(results));
    }

    private boolean hasDenseSource(List<AiReferenceItem> results) {
        return results.stream().anyMatch(item ->
                "VECTOR".equals(item.retrievalSource()) || "HYBRID".equals(item.retrievalSource()));
    }

    /** 管理接口手动触发单文档索引重建，返回入队任务数（始终为 1）。 */
    public long enqueueRebuild(Long docId) {
        if (docId == null) {
            return 0;
        }
        enqueueIndexJob(docId, ACTION_REBUILD);
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        return 1;
    }

    /**
     * 批量重建整个空间（或全量）的 RAG 索引。
     * spaceId 为 null 时重建所有已发布文档，适合模型升级或向量维度变更后的全量重建。
     * 注意：文档量大时入队任务数可能很多，consumer worker 会按序处理。
     */
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

    /**
     * 返回 RAG 系统当前健康状态快照，用于 GET /api/rag/status 接口。
     * 三个关键指标需全部为 true 才视为正常：qdrantAvailable、embeddingAvailable、memoryGatePassed。
     */
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
        // 单次调度只领取一个任务，降低 2C/2G 机器上的内存尖峰；
        // 如果以后要并发索引，需要同时改锁领取策略和 embedding 服务容量。
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

    /**
     * 混合检索：关键词召回 + 向量召回，融合后返回 topK 条引用。
     * 向量召回失败时自动降级返回纯关键词结果，不向上抛异常。
     */
    private List<AiReferenceItem> hybridSearch(String safeQuery, Long spaceId, int topK, RagSearchScope scope) {
        List<Long> visibleSpaceIds = visibleSpaceIds(scope, spaceId);
        // 混合检索先分别取 keyword/dense 候选，再做排序融合。
        // 这样 embedding 服务短暂不可用时 keyword 仍能兜底，用户不会直接得到空答案。
        List<ReferenceCandidate> keywordCandidates = keywordCandidates(
                safeQuery, ragProperties.getRetrieval().getKeywordTopK(), visibleSpaceIds);
        List<ReferenceCandidate> denseCandidates = denseCandidates(
                safeQuery, spaceId, ragProperties.getRetrieval().getDenseTopK(), visibleSpaceIds);
        List<AiReferenceItem> fused = fuseCandidates(denseCandidates, keywordCandidates, topK);
        if (!fused.isEmpty()) {
            return fused;
        }
        return keywordCandidates.stream().limit(Math.max(1, topK)).map(ReferenceCandidate::toReference).toList();
    }

    /**
     * 向量召回路径：query 向量化 → Qdrant 搜索 → JVM 内二次权限过滤。
     * 因 Qdrant 的 filter 无法完整表达多空间权限，先放大召回池（topK*8），再在 JVM 侧过滤。
     * embedding 服务或内存门槛不满足时返回空列表，hybridSearch 自动降级。
     */
    private List<ReferenceCandidate> denseCandidates(String safeQuery, Long spaceId, int topK, List<Long> visibleSpaceIds) {
        if (safeQuery.isBlank()
                || !resourceGuardService.localEmbeddingAllowed()
                || visibleSpaceIds != null && visibleSpaceIds.isEmpty()) {
            return List.of();
        }
        List<Double> queryVector = embeddingClient.embedQuery(safeQuery);
        if (queryVector.isEmpty()) {
            return List.of();
        }
        Long qdrantSpaceId = visibleSpaceIds != null && visibleSpaceIds.size() == 1 ? visibleSpaceIds.get(0) : spaceId;
        // 多空间权限过滤不能完全下推给 Qdrant，所以先扩大召回池，再在 JVM 内按 visibleSpaceIds 二次过滤。
        int qdrantTopK = visibleSpaceIds == null ? Math.max(1, topK) : Math.max(topK * 8, topK + 20);
        return qdrantVectorStore.search(queryVector, qdrantSpaceId, Math.max(1, qdrantTopK))
                .stream()
                .map(result -> candidateFromPayload(result.pointId(), result.score(), result.payload(), safeQuery))
                .filter(Objects::nonNull)
                .filter(candidate -> visibleSpaceIds == null || visibleSpaceIds.contains(candidate.spaceId()))
                .limit(Math.max(1, topK))
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

    /**
     * 关键词召回路径：查最近 80 篇已发布文档的切片，按多字段加权评分排序。
     * 顺带触发懒补建（enqueueMissingIndexes）：首次被访问的未索引文档自动入队向量化任务。
     * 无切片记录时降级为文档维度返回（fallbackDocs）。
     */
    private List<ReferenceCandidate> keywordCandidates(String safeQuery, int topK, List<Long> visibleSpaceIds) {
        if (visibleSpaceIds != null && visibleSpaceIds.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0)
                .eq(KnowledgeDoc::getDocStatus, "PUBLISHED")
                .in(visibleSpaceIds != null, KnowledgeDoc::getSpaceId, visibleSpaceIds)
                .orderByDesc(KnowledgeDoc::getUpdatedAt)
                .last("LIMIT 80"));
        if (docs.isEmpty()) {
            return List.of();
        }
        // 关键词召回访问到已发布文档时顺手补建缺失索引，能让旧数据在第一次被问到后自动进入 RAG worker。
        enqueueMissingIndexes(docs);
        log.debug("RAG关键词检索 spaces={} topK={} 候选文档数={}", visibleSpaceIds == null ? "all" : visibleSpaceIds, topK, docs.size());
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

    /**
     * RRF（Reciprocal Rank Fusion）融合排序。
     * 按名次倒数加权（平滑常量 60），dense 路和 keyword 路分别用配置的权重叠加，
     * 最终按 fusionScore 降序取 topK，避免任何一路的第一名完全主导结果。
     */
    private List<AiReferenceItem> fuseCandidates(
            List<ReferenceCandidate> denseCandidates,
            List<ReferenceCandidate> keywordCandidates,
            int topK
    ) {
        Map<String, FusionCandidate> fused = new LinkedHashMap<>();
        double denseWeight = ragProperties.getRetrieval().getDenseWeight();
        double keywordWeight = ragProperties.getRetrieval().getKeywordWeight();
        // 这里采用 RRF 的思想：名次越靠前加分越高，dense/keyword 用不同权重相加。
        // 使用 60 作为平滑常量，避免某一路召回的第一名绝对压制另一路的多个强相关候选。
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

    /**
     * 文档向量化重建（worker 执行体）。
     * 先清除旧数据（processDelete），再切块、逐块调 embedding 服务、写 Qdrant 和 MySQL。
     * 内存不足时抛 IllegalStateException，worker 会将任务标记 FAILED 并等待退避重试。
     */
    private void processRebuild(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        // 先删除再重建，保证同一 doc/version 重新发布后不会残留旧切片。
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
            // MySQL 先落一条 PENDING 切片，后续任何 embedding/Qdrant 异常都能在后台页面看到失败位置。
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
            // Qdrant payload 保存展示和权限过滤所需字段，读路径不用再回表才能拼引用来源。
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

    /**
     * 构建存入 Qdrant point 的 payload 字段。
     * payload 包含检索命中后拼装引用卡片所需的全部信息（docId、title、chunk、权限字段等），
     * 读路径（candidateFromPayload）不需要回表查 MySQL，显著减少检索延迟。
     */
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
        payload.put("teamId", space == null ? null : space.getTeamId());
        payload.put("visibility", space == null ? null : space.getVisibility());
        payload.put("ownerId", space == null ? null : space.getOwnerId());
        payload.put("versionNo", doc.getVersionNo());
        payload.put("chunkIndex", embedding.getChunkIndex());
        payload.put("chunkText", embedding.getChunkText());
        payload.put("docStatus", doc.getDocStatus());
        payload.put("contentHash", contentHash);
        payload.put("updatedAt", doc.getUpdatedAt() == null ? null : doc.getUpdatedAt().toString());
        return payload;
    }

    private RagSearchScope searchScope(Long userId) {
        if (userId == null) {
            return RagSearchScope.anonymous();
        }
        if (permissionQueryService != null
                && permissionQueryService.listRoleCodes(userId).contains(SUPER_ADMIN_ROLE)) {
            return RagSearchScope.unrestricted(userId);
        }
        // 普通用户只能检索自己所在团队或自己拥有团队下的知识空间。
        // 这里把团队 ID 收敛进 RagSearchScope，后续 visibleSpaceIds 再做统一过滤。
        Set<Long> teamIds = new LinkedHashSet<>();
        if (teamMemberMapper != null) {
            teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getDeleted, 0)
                            .eq(TeamMember::getStatus, 1)
                            .eq(TeamMember::getUserId, userId))
                    .stream()
                    .map(TeamMember::getTeamId)
                    .filter(Objects::nonNull)
                    .forEach(teamIds::add);
        }
        if (teamMapper != null) {
            teamMapper.selectList(new LambdaQueryWrapper<Team>()
                            .eq(Team::getDeleted, 0)
                            .eq(Team::getStatus, 1)
                            .eq(Team::getOwnerId, userId))
                    .stream()
                    .map(Team::getId)
                    .filter(Objects::nonNull)
                    .forEach(teamIds::add);
        }
        return new RagSearchScope(userId, false, teamIds);
    }

    /**
     * 返回 null 表示无限制；返回空集合表示当前用户没有可检索空间。
     */
    private List<Long> visibleSpaceIds(RagSearchScope scope, Long requestedSpaceId) {
        if (scope.unrestricted()) {
            return requestedSpaceId == null ? null : List.of(requestedSpaceId);
        }
        List<KnowledgeSpace> spaces = spaceMapper.selectList(new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getDeleted, 0)
                .eq(requestedSpaceId != null, KnowledgeSpace::getId, requestedSpaceId));
        return spaces.stream()
                .filter(scope::canAccess)
                .map(KnowledgeSpace::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

    /**
     * 固定窗口切块（带重叠），不做语义分句。
     * CHUNK_SIZE=720 字符 + CHUNK_OVERLAP=120 字符的滑动窗口策略，
     * 目标是稳定、低成本地在 2C/2G 小机器上运行，复杂分句可后续替换。
     */
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
            // 通过回退 overlap 个字符制造重叠窗口，而不是按句号切分；
            // 当前目标是稳定、低成本地跑在小机器上，复杂分句可后续替换为独立 chunker。
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
            // 中文没有空格分词时，额外加入单字 token 作为弱匹配信号。
            // 这不是语义分词，只是保证关键词兜底在没有 embedding 时仍有基本可用性。
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

    /**
     * 生成幂等的 Qdrant point UUID（UUID v3，基于内容签名）。
     * 同一 docId/versionNo/chunkIndex/contentHash 组合总是产生相同 UUID，
     * 保证重建时 upsert 而非重复插入，不会在 Qdrant 中产生重复向量点。
     */
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
            // retrievalSource 给前端和评测脚本区分召回来源：
            // VECTOR 表示只由向量命中，KEYWORD 表示只由关键词命中，HYBRID 表示两路都支持。
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

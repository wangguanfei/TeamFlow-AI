package com.teamflow.ai.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.teamflow.ai.common.cache.JsonCacheService;
import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import com.teamflow.ai.modules.ai.entity.AiEmbedding;
import com.teamflow.ai.modules.ai.mapper.AiEmbeddingMapper;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeDoc;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeDocMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeSpaceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiKnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeIndexService.class);

    private static final int CHUNK_SIZE = 720;
    private static final int CHUNK_OVERLAP = 120;

    /** RAG 检索结果缓存：每次聊天都对全部已发布文档做评分排序，成本高；按 query+space+topK 缓存。 */
    private static final String RAG_KEY_PREFIX = "ai:rag:";
    private static final Duration RAG_TTL = Duration.ofMinutes(10);

    private final AiEmbeddingMapper embeddingMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final JsonCacheService jsonCacheService;

    public AiKnowledgeIndexService(
            AiEmbeddingMapper embeddingMapper,
            KnowledgeDocMapper docMapper,
            KnowledgeSpaceMapper spaceMapper,
            JsonCacheService jsonCacheService
    ) {
        this.embeddingMapper = embeddingMapper;
        this.docMapper = docMapper;
        this.spaceMapper = spaceMapper;
        this.jsonCacheService = jsonCacheService;
    }

    @Transactional
    public void rebuildDocumentIndex(KnowledgeDoc doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        embeddingMapper.delete(new LambdaQueryWrapper<AiEmbedding>().eq(AiEmbedding::getDocId, doc.getId()));
        // 索引发生变化（含撤销发布场景），先失效全部 RAG 检索缓存，避免引用到旧文档内容
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
        if (!"PUBLISHED".equalsIgnoreCase(doc.getDocStatus())) {
            return;
        }
        String text = indexableText(doc);
        if (text.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> chunks = splitChunks(text);
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            AiEmbedding embedding = new AiEmbedding();
            embedding.setDocId(doc.getId());
            embedding.setChunkIndex(index);
            embedding.setChunkText(chunk);
            embedding.setEmbeddingHash(sha256(doc.getId() + ":" + doc.getVersionNo() + ":" + index + ":" + chunk));
            embedding.setEmbeddingText("mock-vector:" + compactKeywords(chunk));
            embedding.setCreatedAt(now);
            embedding.setUpdatedAt(now);
            embeddingMapper.insert(embedding);
        }
        log.debug("重建文档向量索引 docId={} 版本号={} 分块数={}", doc.getId(), doc.getVersionNo(), chunks.size());
    }

    @Transactional
    public void deleteDocumentIndex(Long docId) {
        if (docId == null) {
            return;
        }
        embeddingMapper.delete(new LambdaQueryWrapper<AiEmbedding>().eq(AiEmbedding::getDocId, docId));
        jsonCacheService.evictByPrefix(RAG_KEY_PREFIX);
    }

    @Transactional
    public List<AiReferenceItem> searchReferences(String query, Long spaceId, int topK) {
        String safeQuery = normalize(query);
        // 命中缓存即省去全量文档评分排序；key 用 query 摘要 + space + topK，未命中时 loader 跑原检索
        String cacheKey = RAG_KEY_PREFIX + (spaceId == null ? "all" : spaceId) + ":" + Math.max(1, topK) + ":" + sha256(safeQuery);
        return jsonCacheService.getOrLoad(cacheKey, RAG_TTL, new TypeReference<List<AiReferenceItem>>() {},
                () -> doSearchReferences(safeQuery, spaceId, topK));
    }

    private List<AiReferenceItem> doSearchReferences(String safeQuery, Long spaceId, int topK) {
        List<KnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0)
                .eq(KnowledgeDoc::getDocStatus, "PUBLISHED")
                .eq(spaceId != null, KnowledgeDoc::getSpaceId, spaceId)
                .orderByDesc(KnowledgeDoc::getUpdatedAt)
                .last("LIMIT 80"));
        if (docs.isEmpty()) {
            return List.of();
        }
        ensureIndexes(docs);
        log.debug("RAG 检索 spaceId={} topK={} 候选文档数={}", spaceId, topK, docs.size());
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
                        .thenComparing(candidate -> candidate.doc().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, topK))
                .map(ReferenceCandidate::toReference)
                .toList();
    }

    private void ensureIndexes(List<KnowledgeDoc> docs) {
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
                .forEach(this::rebuildDocumentIndex);
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
        return new ReferenceCandidate(doc, spaceMap.get(doc.getSpaceId()), embedding.getChunkIndex(), snippet, round(score));
    }

    private List<AiReferenceItem> fallbackDocs(List<KnowledgeDoc> docs, Map<Long, KnowledgeSpace> spaceMap, String query, int topK) {
        List<String> tokens = tokenize(query);
        return docs.stream()
                .limit(Math.max(1, topK))
                .map(doc -> new AiReferenceItem(
                        doc.getId(),
                        doc.getTitle(),
                        bestSnippet(indexableText(doc), tokens, query),
                        doc.getSpaceId(),
                        spaceMap.get(doc.getSpaceId()) == null ? null : spaceMap.get(doc.getSpaceId()).getSpaceName(),
                        doc.getVersionNo(),
                        0,
                        0.0))
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

    private String compactKeywords(String text) {
        return tokenize(text).stream().limit(16).collect(Collectors.joining(","));
    }

    private String normalize(String value) {
        return normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
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
            KnowledgeDoc doc,
            KnowledgeSpace space,
            Integer chunkIndex,
            String snippet,
            Double score
    ) {
        AiReferenceItem toReference() {
            return new AiReferenceItem(
                    doc.getId(),
                    doc.getTitle(),
                    snippet,
                    doc.getSpaceId(),
                    space == null ? null : space.getSpaceName(),
                    doc.getVersionNo(),
                    chunkIndex,
                    score
            );
        }
    }
}

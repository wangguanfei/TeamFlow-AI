package com.teamflow.ai.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.modules.ai.dto.AiChatRequest;
import com.teamflow.ai.modules.ai.dto.AiChatResponse;
import com.teamflow.ai.modules.ai.dto.AiEmbeddingItem;
import com.teamflow.ai.modules.ai.dto.AiEmbeddingRequest;
import com.teamflow.ai.modules.ai.dto.AiMessageItem;
import com.teamflow.ai.modules.ai.dto.AiMessageRequest;
import com.teamflow.ai.modules.ai.dto.AiProviderStatus;
import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import com.teamflow.ai.modules.ai.dto.AiSessionItem;
import com.teamflow.ai.modules.ai.dto.AiSessionRequest;
import com.teamflow.ai.modules.ai.entity.AiEmbedding;
import com.teamflow.ai.modules.ai.entity.AiMessage;
import com.teamflow.ai.modules.ai.entity.AiSession;
import com.teamflow.ai.modules.ai.mapper.AiEmbeddingMapper;
import com.teamflow.ai.modules.ai.mapper.AiMessageMapper;
import com.teamflow.ai.modules.ai.mapper.AiSessionMapper;
import com.teamflow.ai.modules.ai.provider.AiProvider;
import com.teamflow.ai.modules.ai.provider.AiProperties;
import com.teamflow.ai.modules.ai.provider.MockAiProvider;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeSpaceMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final AtomicLong DEMO_TRANSIENT_ID = new AtomicLong(-1);

    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-stream");
        t.setDaemon(true);
        return t;
    });

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final SysUserMapper userMapper;
    private final AiProvider aiProvider;
    private final MockAiProvider mockAiProvider;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiKnowledgeIndexService knowledgeIndexService;

    public AiService(
            AiSessionMapper sessionMapper,
            AiMessageMapper messageMapper,
            AiEmbeddingMapper embeddingMapper,
            KnowledgeSpaceMapper spaceMapper,
            SysUserMapper userMapper,
            AiProvider aiProvider,
            MockAiProvider mockAiProvider,
            AiProperties properties,
            ObjectMapper objectMapper,
            AiKnowledgeIndexService knowledgeIndexService
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.embeddingMapper = embeddingMapper;
        this.spaceMapper = spaceMapper;
        this.userMapper = userMapper;
        this.aiProvider = aiProvider;
        this.mockAiProvider = mockAiProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    public AiProviderStatus providerStatus() {
        boolean configured = hasText(properties.getApiKey()) && hasText(properties.getBaseUrl());
        return new AiProviderStatus(
                valueOrDefault(properties.getProvider(), configured ? "openai-compatible" : "mock"),
                configured ? valueOrDefault(properties.getModel(), "deepseek-chat") : "mock-ai",
                configured,
                !configured
        );
    }

    public AiProviderStatus providerStatus(Long userId) {
        if (isDemoUser(userId)) {
            return new AiProviderStatus("mock", "mock-ai", false, true);
        }
        return providerStatus();
    }

    @Transactional
    public AiSessionItem createSession(AiSessionRequest request, Long userId) {
        AiSession session = new AiSession();
        fillSession(session, request, userId);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);
        log.info("创建 AI 会话 sessionId={} userId={} type={}", session.getId(), userId, session.getSessionType());
        return toSessionItems(List.of(session)).get(0);
    }

    public PageResult<AiSessionItem> pageSessions(long page, long size, String keyword, Long userId) {
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getDeleted, 0)
                .eq(userId != null, AiSession::getUserId, userId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(AiSession::getSessionTitle, keyword)
                        .or()
                        .like(AiSession::getSessionType, keyword)
                        .or()
                        .like(AiSession::getModelName, keyword))
                .orderByDesc(AiSession::getUpdatedAt)
                .orderByDesc(AiSession::getId);
        Page<AiSession> result = sessionMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toSessionItems(result.getRecords()));
    }

    public AiSessionItem getSession(Long id) {
        return toSessionItems(List.of(getSessionEntity(id))).get(0);
    }

    @Transactional
    public AiSessionItem updateSession(Long id, AiSessionRequest request, Long userId) {
        AiSession session = getSessionEntity(id);
        fillSession(session, request, userId);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        return getSession(id);
    }

    @Transactional
    public void deleteSession(Long id) {
        getSessionEntity(id);
        sessionMapper.deleteById(id);
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getSessionId, id));
        log.info("删除 AI 会话（含消息）sessionId={}", id);
    }

    @Transactional
    public void batchDeleteSessions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> validIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        log.info("批量删除 AI 会话 sessionIds={}", validIds);
        validIds.forEach(this::deleteSession);
    }

    @Transactional
    public AiMessageItem createMessage(AiMessageRequest request) {
        getSessionEntity(request.sessionId());
        AiMessage message = new AiMessage();
        message.setSessionId(request.sessionId());
        message.setRole(normalizeRole(request.role()));
        message.setContent(request.content());
        message.setTokens(request.tokens() == null ? estimateTokens(request.content()) : request.tokens());
        message.setReferencesJson(request.referencesJson());
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        touchSession(request.sessionId());
        return toMessageItems(List.of(message)).get(0);
    }

    public PageResult<AiMessageItem> pageMessages(long page, long size, Long sessionId, String keyword) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(sessionId != null, AiMessage::getSessionId, sessionId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(AiMessage::getContent, keyword)
                        .or()
                        .like(AiMessage::getRole, keyword))
                .orderByAsc(AiMessage::getId);
        Page<AiMessage> result = messageMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toMessageItems(result.getRecords()));
    }

    public AiMessageItem getMessage(Long id) {
        return toMessageItems(List.of(getMessageEntity(id))).get(0);
    }

    @Transactional
    public AiMessageItem updateMessage(Long id, AiMessageRequest request) {
        AiMessage message = getMessageEntity(id);
        message.setSessionId(request.sessionId());
        message.setRole(normalizeRole(request.role()));
        message.setContent(request.content());
        message.setTokens(request.tokens() == null ? estimateTokens(request.content()) : request.tokens());
        message.setReferencesJson(request.referencesJson());
        messageMapper.updateById(message);
        touchSession(request.sessionId());
        return getMessage(id);
    }

    @Transactional
    public void deleteMessage(Long id) {
        messageMapper.deleteById(id);
        log.info("删除 AI 消息 messageId={}", id);
    }

    @Transactional
    public void batchDeleteMessages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> validIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        log.info("批量删除 AI 消息 messageIds={}", validIds);
        validIds.forEach(this::deleteMessage);
    }

    @Transactional
    public AiChatResponse chat(AiChatRequest request, Long userId) {
        if (isDemoUser(userId)) {
            return demoChat(request, userId);
        }
        AiSession session = request.sessionId() == null
                ? createChatSession(request, userId)
                : getSessionEntity(request.sessionId());
        String mode = normalizeMode(request.mode());
        if (!mode.equals(session.getSessionType())) {
            session.setSessionType(mode);
        }
        if (request.spaceId() != null) {
            session.setSpaceId(request.spaceId());
        }

        AiMessage userMessage = saveChatMessage(session.getId(), "USER", request.message(), List.of());
        List<AiReferenceItem> references = resolveRag(request, mode)
                ? searchReferences(request.message(), request.spaceId())
                : List.of();
        List<AiProvider.AiPromptMessage> prompts = buildPrompts(session, mode, references);
        log.info("AI 对话开始 userId={} sessionId={} mode={} 引用条数={} model={}",
                userId, session.getId(), mode, references.size(), request.model());
        long startMillis = System.currentTimeMillis();
        AiProvider.AiAnswer answer = aiProvider.chat(prompts, mode, references, request.model());
        log.info("AI 对话完成 userId={} sessionId={} model={} mock={} 耗时={}ms",
                userId, session.getId(), answer.modelName(), answer.mock(), System.currentTimeMillis() - startMillis);
        AiMessage assistantMessage = saveChatMessage(session.getId(), "ASSISTANT", answer.content(), references);

        session.setModelName(answer.modelName());
        session.setUpdatedAt(LocalDateTime.now());
        if (session.getSessionTitle() == null || session.getSessionTitle().isBlank() || session.getSessionTitle().startsWith("新会话")) {
            session.setSessionTitle(titleFrom(request.message()));
        }
        sessionMapper.updateById(session);

        return new AiChatResponse(
                toSessionItems(List.of(session)).get(0),
                toMessageItems(List.of(userMessage)).get(0),
                toMessageItems(List.of(assistantMessage)).get(0),
                references,
                answer.mock()
        );
    }

    public SseEmitter chatStream(AiChatRequest request, Long userId) {
        if (isDemoUser(userId)) {
            return demoChatStream(request, userId);
        }
        AiSession session = request.sessionId() == null
                ? createChatSession(request, userId)
                : getSessionEntity(request.sessionId());
        String mode = normalizeMode(request.mode());
        if (!mode.equals(session.getSessionType())) {
            session.setSessionType(mode);
            sessionMapper.updateById(session);
        }
        if (request.spaceId() != null) {
            session.setSpaceId(request.spaceId());
        }
        AiMessage userMessage = saveChatMessage(session.getId(), "USER", request.message(), List.of());
        List<AiReferenceItem> references = resolveRag(request, mode)
                ? searchReferences(request.message(), request.spaceId())
                : List.of();
        List<AiProvider.AiPromptMessage> prompts = buildPrompts(session, mode, references);

        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        final AiSession capturedSession = session;
        final AiMessage capturedUserMessage = userMessage;
        final List<AiReferenceItem> capturedReferences = references;
        final String capturedMode = mode;
        final String capturedModel = request.model();

        log.info("AI 流式对话开始 userId={} sessionId={} mode={} 引用条数={} model={}",
                userId, session.getId(), mode, references.size(), request.model());
        final long streamStartMillis = System.currentTimeMillis();
        SSE_EXECUTOR.execute(() -> {
            try {
                AiProvider.AiAnswer answer = aiProvider.chatStream(prompts, capturedMode, capturedReferences, capturedModel, token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(Map.of("type", "token", "content", token))));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                AiMessage assistantMessage = saveChatMessage(capturedSession.getId(), "ASSISTANT", answer.content(), capturedReferences);
                capturedSession.setModelName(answer.modelName());
                capturedSession.setUpdatedAt(LocalDateTime.now());
                if (capturedSession.getSessionTitle() == null || capturedSession.getSessionTitle().isBlank()
                        || capturedSession.getSessionTitle().startsWith("新会话")) {
                    capturedSession.setSessionTitle(titleFrom(request.message()));
                }
                sessionMapper.updateById(capturedSession);

                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("type", "done");
                donePayload.put("session", toSessionItems(List.of(capturedSession)).get(0));
                donePayload.put("userMessage", toMessageItems(List.of(capturedUserMessage)).get(0));
                donePayload.put("assistantMessage", toMessageItems(List.of(assistantMessage)).get(0));
                donePayload.put("references", capturedReferences);
                donePayload.put("mock", answer.mock());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(donePayload)));
                emitter.complete();
                log.info("AI 流式对话完成 userId={} sessionId={} model={} mock={} 耗时={}ms",
                        userId, capturedSession.getId(), answer.modelName(), answer.mock(),
                        System.currentTimeMillis() - streamStartMillis);
            } catch (Exception error) {
                log.error("AI 流式对话失败 userId={} sessionId={} 耗时={}ms",
                        userId, capturedSession.getId(), System.currentTimeMillis() - streamStartMillis, error);
                try {
                    String msg = error.getMessage() == null ? "AI 服务异常" : error.getMessage();
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(Map.of("type", "error", "message", msg))));
                } catch (Exception ignored) {}
                emitter.completeWithError(error);
            }
        });

        return emitter;
    }

    @Transactional
    public AiChatResponse knowledgeAsk(AiChatRequest request, Long userId) {
        return chat(new AiChatRequest(request.sessionId(), request.spaceId(), "KNOWLEDGE", true, request.model(), request.message()), userId);
    }

    @Transactional
    public AiChatResponse documentSummary(AiChatRequest request, Long userId) {
        return chat(new AiChatRequest(request.sessionId(), request.spaceId(), "SUMMARY", request.useKnowledge(), request.model(), request.message()), userId);
    }

    @Transactional
    public AiChatResponse codeGenerate(AiChatRequest request, Long userId) {
        return chat(new AiChatRequest(request.sessionId(), request.spaceId(), "CODE", request.useKnowledge(), request.model(), request.message()), userId);
    }

    private AiChatResponse demoChat(AiChatRequest request, Long userId) {
        String mode = normalizeMode(request.mode());
        List<AiReferenceItem> references = resolveRag(request, mode)
                ? searchReferences(request.message(), request.spaceId())
                : List.of();
        AiProvider.AiAnswer answer = mockAiProvider.chat(buildDemoPrompts(request.message(), mode, references), mode, references);
        LocalDateTime now = LocalDateTime.now();
        Long sessionId = nextDemoTransientId();
        AiSessionItem session = new AiSessionItem(
                sessionId,
                userId,
                "只读演示账号",
                request.spaceId(),
                resolveSpaceName(request.spaceId()),
                titleFrom(request.message()),
                answer.modelName(),
                mode,
                2,
                now,
                now
        );
        AiMessageItem userMessage = new AiMessageItem(
                nextDemoTransientId(),
                sessionId,
                "USER",
                request.message(),
                estimateTokens(request.message()),
                List.of(),
                now
        );
        AiMessageItem assistantMessage = new AiMessageItem(
                nextDemoTransientId(),
                sessionId,
                "ASSISTANT",
                answer.content(),
                answer.tokens(),
                references,
                now
        );
        return new AiChatResponse(session, userMessage, assistantMessage, references, true);
    }

    private SseEmitter demoChatStream(AiChatRequest request, Long userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        SSE_EXECUTOR.execute(() -> {
            try {
                AiChatResponse response = demoChat(request, userId);
                emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(Map.of(
                                "type", "token",
                                "content", response.assistantMessage().content()
                        ))));

                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("type", "done");
                donePayload.put("session", response.session());
                donePayload.put("userMessage", response.userMessage());
                donePayload.put("assistantMessage", response.assistantMessage());
                donePayload.put("references", response.references());
                donePayload.put("mock", true);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(donePayload)));
                emitter.complete();
            } catch (Exception error) {
                try {
                    String msg = error.getMessage() == null ? "AI 演示服务异常" : error.getMessage();
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(Map.of("type", "error", "message", msg))));
                } catch (Exception ignored) {}
                emitter.completeWithError(error);
            }
        });
        return emitter;
    }

    private List<AiProvider.AiPromptMessage> buildDemoPrompts(String message, String mode, List<AiReferenceItem> references) {
        String referenceText = references.isEmpty()
                ? ""
                : "\n\n知识库引用:\n" + references.stream()
                .map(reference -> reference.title() + "\n" + reference.snippet())
                .collect(Collectors.joining("\n"));
        return List.of(
                new AiProvider.AiPromptMessage("SYSTEM", "你是 TeamFlow AI 演示助手。当前是只读演示账号，必须使用 Mock 模式，不调用外部大模型。" + referenceText),
                new AiProvider.AiPromptMessage("USER", message == null ? "" : message)
        );
    }

    /**
     * 是否启用知识库检索（RAG）。
     * useKnowledge 显式给出时以其为准（即便选了 spaceId 也可纯大模型直答）；
     * 为 null 时沿用旧行为兼容老客户端：KNOWLEDGE 模式或带 spaceId 即检索。
     */
    private boolean resolveRag(AiChatRequest request, String mode) {
        if (request.useKnowledge() != null) {
            return request.useKnowledge();
        }
        return "KNOWLEDGE".equals(mode) || request.spaceId() != null;
    }

    @Transactional
    public AiEmbeddingItem createEmbedding(AiEmbeddingRequest request) {
        AiEmbedding embedding = new AiEmbedding();
        fillEmbedding(embedding, request);
        embedding.setCreatedAt(LocalDateTime.now());
        embedding.setUpdatedAt(LocalDateTime.now());
        embeddingMapper.insert(embedding);
        return toEmbeddingItems(List.of(embedding)).get(0);
    }

    public PageResult<AiEmbeddingItem> pageEmbeddings(long page, long size, Long docId, String keyword) {
        LambdaQueryWrapper<AiEmbedding> wrapper = new LambdaQueryWrapper<AiEmbedding>()
                .eq(docId != null, AiEmbedding::getDocId, docId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(AiEmbedding::getChunkText, keyword)
                        .or()
                        .like(AiEmbedding::getEmbeddingHash, keyword))
                .orderByDesc(AiEmbedding::getId);
        Page<AiEmbedding> result = embeddingMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toEmbeddingItems(result.getRecords()));
    }

    public AiEmbeddingItem getEmbedding(Long id) {
        AiEmbedding embedding = embeddingMapper.selectById(id);
        if (embedding == null) {
            throw new BusinessException("向量切片不存在");
        }
        return toEmbeddingItems(List.of(embedding)).get(0);
    }

    @Transactional
    public AiEmbeddingItem updateEmbedding(Long id, AiEmbeddingRequest request) {
        AiEmbedding embedding = embeddingMapper.selectById(id);
        if (embedding == null) {
            throw new BusinessException("向量切片不存在");
        }
        fillEmbedding(embedding, request);
        embedding.setUpdatedAt(LocalDateTime.now());
        embeddingMapper.updateById(embedding);
        return getEmbedding(id);
    }

    @Transactional
    public void deleteEmbedding(Long id) {
        embeddingMapper.deleteById(id);
        log.info("删除向量切片 embeddingId={}", id);
    }

    @Transactional
    public void batchDeleteEmbeddings(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> validIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        log.info("批量删除向量切片 embeddingIds={}", validIds);
        validIds.forEach(this::deleteEmbedding);
    }

    private void fillSession(AiSession session, AiSessionRequest request, Long userId) {
        session.setUserId(userId);
        session.setSpaceId(request.spaceId());
        session.setSessionTitle(request.sessionTitle());
        session.setModelName(request.modelName() == null || request.modelName().isBlank() ? properties.getModel() : request.modelName());
        session.setSessionType(normalizeMode(request.sessionType()));
    }

    private AiSession createChatSession(AiChatRequest request, Long userId) {
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setSpaceId(request.spaceId());
        session.setSessionTitle(titleFrom(request.message()));
        session.setModelName(properties.getModel());
        session.setSessionType(normalizeMode(request.mode()));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);
        return session;
    }

    private AiMessage saveChatMessage(Long sessionId, String role, String content, List<AiReferenceItem> references) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setTokens(estimateTokens(content));
        message.setReferencesJson(writeReferences(references));
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private boolean isDemoUser(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        return user != null && DemoAccountConstants.USERNAME.equals(user.getUsername());
    }

    private Long nextDemoTransientId() {
        return DEMO_TRANSIENT_ID.getAndDecrement();
    }

    private String resolveSpaceName(Long spaceId) {
        if (spaceId == null) {
            return null;
        }
        KnowledgeSpace space = spaceMapper.selectById(spaceId);
        return space == null ? null : space.getSpaceName();
    }

    private List<AiProvider.AiPromptMessage> buildPrompts(AiSession session, String mode, List<AiReferenceItem> references) {
        String systemPrompt = switch (mode) {
            case "KNOWLEDGE" -> "你是 TeamFlow AI 知识库问答助手。优先依据引用资料回答，在关键结论后标注引用编号如 [1]；资料不足时明确说明缺口，并给出可执行的下一步。";
            case "SUMMARY" -> "你是 TeamFlow AI 文档总结助手。输出结构化摘要、风险点和下一步行动。";
            case "CODE" -> "你是 TeamFlow AI 代码助手。回答要工程化、可落地，并注意边界条件。";
            case "SQL" -> "你是 TeamFlow AI SQL 助手。生成 SQL 时说明用途和关键条件。";
            default -> "你是 TeamFlow AI 企业协同平台助手。回答要简洁、准确、适合全栈面试演示。";
        };
        String referenceText = references.isEmpty()
                ? ""
                : "\n\n知识库引用:\n" + references.stream()
                .map(reference -> {
                    int index = references.indexOf(reference) + 1;
                    String source = reference.spaceName() == null ? "" : " / " + reference.spaceName();
                    String version = reference.versionNo() == null ? "" : " v" + reference.versionNo();
                    return "[" + index + "] " + reference.title() + source + version + "\n" + reference.snippet();
                })
                .collect(Collectors.joining("\n"));
        List<AiMessage> recentMessages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, session.getId())
                        .orderByDesc(AiMessage::getId)
                        .last("LIMIT 8"))
                .stream()
                .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                .toList();
        List<AiProvider.AiPromptMessage> prompts = new java.util.ArrayList<>();
        prompts.add(new AiProvider.AiPromptMessage("SYSTEM", systemPrompt + referenceText));
        for (AiMessage message : recentMessages) {
            prompts.add(new AiProvider.AiPromptMessage(message.getRole(), message.getContent()));
        }
        return prompts;
    }

    private List<AiReferenceItem> searchReferences(String keyword, Long spaceId) {
        return knowledgeIndexService.searchReferences(keyword, spaceId, 5);
    }

    private List<AiSessionItem> toSessionItems(List<AiSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = sessions.stream().map(AiSession::getUserId).filter(Objects::nonNull).distinct().toList();
        List<Long> spaceIds = sessions.stream().map(AiSession::getSpaceId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> users = userIds.isEmpty() ? new java.util.HashMap<>() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, KnowledgeSpace> spaces = spaceIds.isEmpty() ? new java.util.HashMap<>() : spaceMapper.selectBatchIds(spaceIds).stream()
                .collect(Collectors.toMap(KnowledgeSpace::getId, Function.identity()));
        return sessions.stream()
                .map(session -> {
                    SysUser user = users.get(session.getUserId());
                    KnowledgeSpace space = spaces.get(session.getSpaceId());
                    long messageCount = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                            .eq(AiMessage::getSessionId, session.getId()));
                    return new AiSessionItem(
                            session.getId(),
                            session.getUserId(),
                            user == null ? null : user.getNickname(),
                            session.getSpaceId(),
                            space == null ? null : space.getSpaceName(),
                            session.getSessionTitle(),
                            session.getModelName(),
                            session.getSessionType(),
                            messageCount,
                            session.getCreatedAt(),
                            session.getUpdatedAt());
                })
                .toList();
    }

    private List<AiMessageItem> toMessageItems(List<AiMessage> messages) {
        return messages.stream()
                .map(message -> new AiMessageItem(
                        message.getId(),
                        message.getSessionId(),
                        message.getRole(),
                        message.getContent(),
                        message.getTokens(),
                        readReferences(message.getReferencesJson()),
                        message.getCreatedAt()))
                .toList();
    }

    private List<AiEmbeddingItem> toEmbeddingItems(List<AiEmbedding> embeddings) {
        return embeddings.stream()
                .map(embedding -> new AiEmbeddingItem(
                        embedding.getId(),
                        embedding.getDocId(),
                        embedding.getChunkIndex(),
                        embedding.getChunkText(),
                        embedding.getEmbeddingHash(),
                        embedding.getEmbeddingText(),
                        embedding.getCreatedAt(),
                        embedding.getUpdatedAt()))
                .toList();
    }

    private AiSession getSessionEntity(Long id) {
        AiSession session = sessionMapper.selectById(id);
        if (session == null || (session.getDeleted() != null && session.getDeleted() == 1)) {
            throw new BusinessException("AI会话不存在");
        }
        return session;
    }

    private AiMessage getMessageEntity(Long id) {
        AiMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new BusinessException("AI消息不存在");
        }
        return message;
    }

    private void touchSession(Long sessionId) {
        AiSession session = getSessionEntity(sessionId);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void fillEmbedding(AiEmbedding embedding, AiEmbeddingRequest request) {
        embedding.setDocId(request.docId());
        embedding.setChunkIndex(request.chunkIndex() == null ? 0 : request.chunkIndex());
        embedding.setChunkText(request.chunkText());
        embedding.setEmbeddingHash(request.embeddingHash() == null || request.embeddingHash().isBlank()
                ? sha256(request.chunkText())
                : request.embeddingHash());
        embedding.setEmbeddingText(request.embeddingText() == null || request.embeddingText().isBlank()
                ? "demo-vector:" + sha256(request.chunkText())
                : request.embeddingText());
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "CHAT";
        }
        return mode.toUpperCase();
    }

    private String normalizeRole(String role) {
        if ("ASSISTANT".equalsIgnoreCase(role)) {
            return "ASSISTANT";
        }
        if ("SYSTEM".equalsIgnoreCase(role)) {
            return "SYSTEM";
        }
        return "USER";
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "新会话";
        }
        String title = message.replaceAll("\\s+", " ").trim();
        return title.length() > 24 ? title.substring(0, 24) : title;
    }

    private int estimateTokens(String content) {
        return Math.max(1, (content == null ? 0 : content.length()) / 4);
    }

    private String snippet(String contentText, String contentMd) {
        String text = contentText == null || contentText.isBlank() ? contentMd : contentText;
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String writeReferences(List<AiReferenceItem> references) {
        if (references == null || references.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private List<AiReferenceItem> readReferences(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}

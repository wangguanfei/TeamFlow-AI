package com.teamflow.ai.modules.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.agent.dto.AgentActionResult;
import com.teamflow.ai.modules.agent.dto.AgentChatRequest;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolResult;
import com.teamflow.ai.modules.ai.entity.AiMessage;
import com.teamflow.ai.modules.ai.entity.AiSession;
import com.teamflow.ai.modules.ai.mapper.AiMessageMapper;
import com.teamflow.ai.modules.ai.mapper.AiSessionMapper;
import com.teamflow.ai.modules.ai.provider.AiProperties;
import com.teamflow.ai.modules.ai.provider.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final ExecutorService AGENT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "agent-sse");
        thread.setDaemon(true);
        return thread;
    });

    private final AiProvider aiProvider;
    private final AiProperties aiProperties;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolExecutor toolExecutor;
    private final PendingActionStore pendingActionStore;
    private final AgentActionService actionService;
    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(
            AiProvider aiProvider,
            AiProperties aiProperties,
            AgentToolRegistry toolRegistry,
            AgentToolExecutor toolExecutor,
            PendingActionStore pendingActionStore,
            AgentActionService actionService,
            AiSessionMapper sessionMapper,
            AiMessageMapper messageMapper,
            ObjectMapper objectMapper
    ) {
        this.aiProvider = aiProvider;
        this.aiProperties = aiProperties;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.pendingActionStore = pendingActionStore;
        this.actionService = actionService;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public SseEmitter chat(AgentChatRequest request, UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());

        AGENT_EXECUTOR.execute(() -> runChat(request, principal, emitter));
        return emitter;
    }

    public AgentActionResult confirm(String confirmToken, UserPrincipal principal) {
        PendingActionStore.PendingAction pending = pendingActionStore.consume(confirmToken, principal.getUserId());
        AgentTool tool = toolRegistry.getRequired(pending.toolName());
        long startMs = System.currentTimeMillis();
        try {
            ToolResult result = toolExecutor.execute(tool, pending.arguments(), principal);
            actionService.markExecuted(pending.actionId(), principal.getUserId(), result, System.currentTimeMillis() - startMs);
            return new AgentActionResult(
                    pending.actionId(),
                    "EXECUTED",
                    result.summary(),
                    result.targetType(),
                    result.targetId(),
                    result.url()
            );
        } catch (RuntimeException error) {
            actionService.markFailed(pending.actionId(), error.getMessage(), System.currentTimeMillis() - startMs);
            throw error;
        }
    }

    public AgentActionResult cancel(String confirmToken, UserPrincipal principal) {
        PendingActionStore.PendingAction pending = pendingActionStore.cancel(confirmToken, principal.getUserId());
        if (pending == null) {
            return new AgentActionResult(null, "CANCELLED", "待确认操作不存在或已过期", null, null, null);
        }
        actionService.markCancelled(pending.actionId(), principal.getUserId());
        return new AgentActionResult(pending.actionId(), "CANCELLED", "已取消，本次没有产生任何变更", null, null, null);
    }

    private void runChat(AgentChatRequest request, UserPrincipal principal, SseEmitter emitter) {
        try {
            AiSession session = resolveSession(request, principal.getUserId());
            AiMessage userMessage = saveMessage(session.getId(), "USER", request.message());
            send(emitter, event("agent_status", Map.of("status", "THINKING", "message", "正在分析你的需求")));

            List<AiProvider.AiPromptMessage> prompts = buildPrompts(session);
            AiProvider.AiAgentAnswer answer = aiProvider.chatWithTools(prompts, toolRegistry.providerDefinitions(), request.model());
            session.setModelName(answer.modelName());
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);

            if (answer.toolCalls() == null || answer.toolCalls().isEmpty()) {
                String content = answer.content() == null || answer.content().isBlank()
                        ? "我暂时没有识别到可执行的企业助理操作，请换一种说法。"
                        : answer.content();
                AiMessage assistantMessage = saveMessage(session.getId(), "ASSISTANT", content);
                send(emitter, event("agent_token", Map.of("content", content)));
                send(emitter, doneEvent(session, assistantMessage, answer.mock()));
                emitter.complete();
                return;
            }

            for (AiProvider.AiToolCall call : answer.toolCalls()) {
                AgentTool tool = toolRegistry.getRequired(call.name());
                send(emitter, event("agent_tool_call", Map.of(
                        "toolName", tool.definition().name(),
                        "toolLabel", tool.definition().label(),
                        "write", tool.definition().write()
                )));
                if (tool.definition().write()) {
                    Map<String, Object> preview = toolExecutor.preview(tool, call.arguments(), principal);
                    var action = actionService.create(session.getId(), userMessage.getId(), principal.getUserId(), tool, call.arguments(), preview, "PENDING");
                    String token = pendingActionStore.save(new PendingActionStore.PendingAction(
                            action.getId(),
                            principal.getUserId(),
                            session.getId(),
                            tool.definition().name(),
                            call.arguments(),
                            preview,
                            tool.definition().requiredPermission()
                    ));
                    actionService.updateTokenHash(action.getId(), pendingActionStore.tokenHash(token));
                    Map<String, Object> pendingPayload = new LinkedHashMap<>();
                    pendingPayload.put("actionId", action.getId());
                    pendingPayload.put("confirmToken", token);
                    pendingPayload.put("toolName", tool.definition().name());
                    pendingPayload.put("toolLabel", tool.definition().label());
                    pendingPayload.put("preview", preview);
                    send(emitter, event("agent_pending_action", pendingPayload));
                    AiMessage assistantMessage = saveMessage(session.getId(), "ASSISTANT", "已生成「" + tool.definition().label() + "」预览，请确认后执行。");
                    send(emitter, doneEvent(session, assistantMessage, answer.mock()));
                    emitter.complete();
                    return;
                }

                long startMs = System.currentTimeMillis();
                var action = actionService.create(session.getId(), userMessage.getId(), principal.getUserId(), tool, call.arguments(), null, "RUNNING");
                ToolResult result = toolExecutor.execute(tool, call.arguments(), principal);
                actionService.markExecuted(action.getId(), principal.getUserId(), result, System.currentTimeMillis() - startMs);
                send(emitter, event("agent_tool_result", Map.of(
                        "toolName", tool.definition().name(),
                        "success", result.success(),
                        "summary", result.summary(),
                        "data", result.data()
                )));
                AiMessage assistantMessage = saveMessage(session.getId(), "ASSISTANT", result.summary() + "\n\n" + toPrettyJson(result.data()));
                send(emitter, doneEvent(session, assistantMessage, answer.mock()));
                emitter.complete();
                return;
            }
        } catch (Exception error) {
            log.error("AI 企业助理执行失败 userId={}", principal.getUserId(), error);
            try {
                send(emitter, event("agent_error", Map.of("message", error.getMessage() == null ? "AI 企业助理执行失败" : error.getMessage())));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(error);
        }
    }

    private AiSession resolveSession(AgentChatRequest request, Long userId) {
        if (request.sessionId() != null) {
            AiSession session = sessionMapper.selectById(request.sessionId());
            if (session == null || Integer.valueOf(1).equals(session.getDeleted())) {
                throw new BusinessException("AI 会话不存在");
            }
            if (!session.getUserId().equals(userId)) {
                throw new BusinessException(403, "AI 会话无访问权限");
            }
            session.setSessionType("AGENT");
            if (request.spaceId() != null) {
                session.setSpaceId(request.spaceId());
            }
            return session;
        }
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setSpaceId(request.spaceId());
        session.setSessionTitle(titleFrom(request.message()));
        session.setModelName(aiProperties.getModel());
        session.setSessionType("AGENT");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);
        return session;
    }

    private List<AiProvider.AiPromptMessage> buildPrompts(AiSession session) {
        List<AiMessage> recentMessages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, session.getId())
                        .orderByDesc(AiMessage::getId)
                        .last("LIMIT 8"))
                .stream()
                .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                .toList();
        List<AiProvider.AiPromptMessage> prompts = new java.util.ArrayList<>();
        prompts.add(new AiProvider.AiPromptMessage("SYSTEM", """
                你是 TeamFlow AI 企业助理，服务对象是中小微企业。
                你可以在当前用户权限范围内调用工具完成办事动作。
                写操作必须只返回工具调用，由系统展示预览并等待用户确认。
                创建待办事项只要求标题明确；项目、负责人、截止时间缺失时可以省略，系统会按默认规则补全。
                当用户明确表达创建、安排、跟进、提醒某个事项时，应优先调用 create_task，不要为了可选字段反复追问。
                当用户表达完成、开始、进入测试、关闭任务时，调用 update_task_status。
                当用户表达把任务交给某人、负责人改成某人时，调用 assign_task。
                当用户表达催办、提醒、通知某人跟进任务时，调用 send_notification。
                当用户需要进展、项目简报、任务统计时，调用 query_project_summary。
                当用户需要老板每日经营简报、今日经营情况时，调用 daily_business_brief。
                当用户询问制度、流程、规范、资料或知识库内容时，调用 search_knowledge。
                只有必填参数缺失或无法定位唯一业务对象时才追问；不要编造任务ID、用户ID或知识库空间ID。
                """));
        for (AiMessage message : recentMessages) {
            prompts.add(new AiProvider.AiPromptMessage(message.getRole(), message.getContent()));
        }
        return prompts;
    }

    private AiMessage saveMessage(Long sessionId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setTokens(Math.max(1, content == null ? 1 : content.length() / 4));
        message.setReferencesJson("[]");
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private Map<String, Object> doneEvent(AiSession session, AiMessage assistantMessage, boolean mock) {
        return event("agent_done", Map.of(
                "sessionId", session.getId(),
                "assistantMessageId", assistantMessage.getId(),
                "mock", mock
        ));
    }

    private Map<String, Object> event(String type, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.putAll(payload);
        return event;
    }

    private void send(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "AI 企业助理会话";
        }
        String trimmed = message.trim();
        return trimmed.length() > 24 ? trimmed.substring(0, 24) + "..." : trimmed;
    }
}

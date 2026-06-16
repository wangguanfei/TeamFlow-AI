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
                AiMessage assistantMessage = saveMessage(session.getId(), "ASSISTANT", toReadableToolMessage(tool.definition().name(), result));
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

    private String toReadableToolMessage(String toolName, ToolResult result) {
        Map<String, Object> data = result.data() == null ? Map.of() : result.data();
        String content = switch (toolName) {
            case "daily_business_brief" -> formatBusinessBrief(result.summary(), data);
            case "query_project_summary" -> formatProjectSummary(result.summary(), data);
            case "list_my_tasks" -> formatTaskList(result.summary(), data);
            case "search_knowledge" -> formatKnowledgeSearch(result.summary(), data);
            default -> safeSummary(result.summary());
        };
        return appendRelatedLink(content, result.url());
    }

    private String formatBusinessBrief(String summary, Map<String, Object> data) {
        StringBuilder builder = new StringBuilder("### 老板每日经营简报\n\n");
        builder.append("- 日期：").append(valueText(data.get("briefDate"))).append('\n');
        builder.append("- 当前事项：").append(valueText(data.get("total"))).append(" 个\n");
        builder.append("- 今日到期：").append(valueText(data.get("todayDueCount"))).append(" 个\n");
        builder.append("- 逾期事项：").append(valueText(data.get("overdueCount"))).append(" 个\n");
        builder.append("- 高优先级待跟进：").append(valueText(data.get("highPriorityOpenCount"))).append(" 个");
        appendStatusCounts(builder, data.get("statusCounts"));
        appendTaskItems(builder, "重点风险", data.get("riskTasks"), 5);
        appendTaskItems(builder, "近期事项", data.get("recentTasks"), 5);
        return builder.toString();
    }

    private String formatProjectSummary(String summary, Map<String, Object> data) {
        List<?> recentTasks = listValue(data.get("recentTasks"));
        Object total = data.get("total");
        if (isSingleTaskSummary(total, recentTasks)) {
            return formatSingleTaskProgress(recentTasks.get(0));
        }
        StringBuilder builder = new StringBuilder("### 工作进展简报\n\n");
        builder.append(safeSummary(summary));
        appendStatusCounts(builder, data.get("statusCounts"));
        appendTaskItems(builder, "重点风险", data.get("riskTasks"), 5);
        appendTaskItems(builder, "近期事项", recentTasks, 6);
        return builder.toString();
    }

    private boolean isSingleTaskSummary(Object total, List<?> tasks) {
        if (tasks.size() != 1) {
            return false;
        }
        if (total instanceof Number number) {
            return number.longValue() == 1L;
        }
        return "1".equals(String.valueOf(total));
    }

    private String formatSingleTaskProgress(Object value) {
        Map<?, ?> task = mapValue(value);
        String taskNo = optionalText(task.get("taskNo"));
        String title = valueText(task.get("title"));
        String status = optionalText(task.get("status"));
        String priority = optionalText(task.get("priority"));
        String assignee = optionalText(task.get("assigneeName"));
        String projectName = optionalText(task.get("projectName"));
        String dueTime = optionalText(task.get("dueTime"));

        StringBuilder builder = new StringBuilder("### 任务进展\n\n");
        builder.append("**").append(taskNo == null ? title : taskNo + " " + title).append("**\n\n");
        builder.append("- 当前状态：").append(status == null ? "未设置" : statusLabel(status)).append('\n');
        if (priority != null) {
            builder.append("- 优先级：").append(priorityLabel(priority)).append('\n');
        }
        if (assignee != null) {
            builder.append("- 负责人：").append(assignee).append('\n');
        }
        if (projectName != null) {
            builder.append("- 所属项目：").append(projectName).append('\n');
        }
        if (dueTime != null) {
            builder.append("- 截止时间：").append(dueTime.replace('T', ' ')).append('\n');
        }
        if ("DONE".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            builder.append("\n该任务已完成或关闭。");
        } else if ("TESTING".equalsIgnoreCase(status)) {
            builder.append("\n该任务当前处于测试阶段，重点关注验收结果和缺陷反馈。");
        } else if ("DOING".equalsIgnoreCase(status)) {
            builder.append("\n该任务正在推进中，建议继续跟进负责人更新。");
        } else {
            builder.append("\n该任务尚未完成，可继续跟进下一步处理。");
        }
        return builder.toString();
    }

    private String formatTaskList(String summary, Map<String, Object> data) {
        List<?> tasks = listValue(data.get("tasks"));
        Object count = data.get("count");
        if (isSingleTaskSummary(count, tasks)) {
            return formatSingleTaskProgress(tasks.get(0));
        }
        StringBuilder builder = new StringBuilder(safeSummary(summary));
        appendTaskItems(builder, "待办事项", tasks, 10);
        return builder.toString();
    }

    private String formatKnowledgeSearch(String summary, Map<String, Object> data) {
        StringBuilder builder = new StringBuilder(safeSummary(summary));
        List<?> references = listValue(data.get("references"));
        if (references.isEmpty()) {
            return builder.toString();
        }
        builder.append("\n\n**匹配资料**");
        int index = 1;
        for (Object item : references.stream().limit(5).toList()) {
            Map<?, ?> reference = mapValue(item);
            String title = valueText(reference.get("title"));
            String spaceName = optionalText(reference.get("spaceName"));
            String score = optionalText(reference.get("score"));
            String snippet = optionalText(reference.get("snippet"));
            builder.append("\n").append(index++).append(". ").append(title);
            if (spaceName != null || score != null) {
                builder.append("（");
                if (spaceName != null) builder.append(spaceName);
                if (spaceName != null && score != null) builder.append("，");
                if (score != null) builder.append("相关度 ").append(score);
                builder.append("）");
            }
            if (snippet != null) {
                builder.append("\n   ").append(truncate(snippet, 120));
            }
        }
        return builder.toString();
    }

    private void appendStatusCounts(StringBuilder builder, Object value) {
        Map<?, ?> counts = mapValue(value);
        if (counts.isEmpty()) {
            return;
        }
        List<String> orderedStatuses = List.of("TODO", "DOING", "TESTING", "DONE", "CLOSED");
        List<String> parts = new java.util.ArrayList<>();
        for (String status : orderedStatuses) {
            Object count = counts.get(status);
            if (count != null) {
                parts.add(statusLabel(status) + " " + count + " 个");
            }
        }
        for (Map.Entry<?, ?> entry : counts.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!orderedStatuses.contains(key)) {
                parts.add(statusLabel(key) + " " + entry.getValue() + " 个");
            }
        }
        if (!parts.isEmpty()) {
            builder.append("\n\n**状态分布**：").append(String.join("，", parts));
        }
    }

    private void appendTaskItems(StringBuilder builder, String title, Object value, int limit) {
        List<?> tasks = listValue(value);
        if (tasks.isEmpty()) {
            return;
        }
        builder.append("\n\n**").append(title).append("**");
        for (Object item : tasks.stream().limit(limit).toList()) {
            Map<?, ?> task = mapValue(item);
            String taskTitle = valueText(task.get("title"));
            String status = optionalText(task.get("status"));
            String priority = optionalText(task.get("priority"));
            String assignee = optionalText(task.get("assigneeName"));
            String dueTime = optionalText(task.get("dueTime"));
            builder.append("\n- ").append(taskTitle);
            List<String> meta = new java.util.ArrayList<>();
            if (status != null) meta.add(statusLabel(status));
            if (priority != null) meta.add(priorityLabel(priority));
            if (assignee != null) meta.add("负责人：" + assignee);
            if (dueTime != null) meta.add("截止：" + dueTime.replace('T', ' '));
            if (!meta.isEmpty()) {
                builder.append("（").append(String.join("，", meta)).append("）");
            }
        }
    }

    private String appendRelatedLink(String content, String url) {
        if (url == null || url.isBlank()) {
            return content;
        }
        return content + "\n\n[查看相关页面](" + url + ")";
    }

    private String safeSummary(String summary) {
        return summary == null || summary.isBlank() ? "工具执行完成" : summary.trim();
    }

    private Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String valueText(Object value) {
        String text = optionalText(value);
        return text == null ? "-" : text;
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "TODO" -> "待办";
            case "DOING" -> "进行中";
            case "TESTING" -> "测试中";
            case "DONE" -> "已完成";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    private String priorityLabel(String priority) {
        return switch (priority == null ? "" : priority.toUpperCase()) {
            case "LOW" -> "低优先级";
            case "MEDIUM" -> "中优先级";
            case "HIGH" -> "高优先级";
            case "URGENT" -> "紧急";
            default -> priority;
        };
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "AI 企业助理会话";
        }
        String trimmed = message.trim();
        return trimmed.length() > 24 ? trimmed.substring(0, 24) + "..." : trimmed;
    }
}

package com.teamflow.ai.modules.ai.provider;

import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 本地/演示环境的 Mock AI 实现。
 *
 * <p>当没有配置真实模型 baseUrl/apiKey，或者上游模型调用失败时会回退到这里。
 * Mock 不追求自然语言质量，重点是保证聊天、RAG 引用展示、Agent 工具调用和写操作确认流程
 * 在无外部模型依赖时仍能完整演示。</p>
 */
@Component
public class MockAiProvider {

    public AiProvider.AiAnswer chat(List<AiProvider.AiPromptMessage> messages, String mode, List<AiReferenceItem> references) {
        String latestUserMessage = messages.stream()
                .filter(message -> "USER".equalsIgnoreCase(message.role()))
                .reduce((first, second) -> second)
                .map(AiProvider.AiPromptMessage::content)
                .orElse("");
        StringBuilder answer = new StringBuilder();
        answer.append("MockAIProvider 已启用。");
        answer.append("\n\n");
        answer.append("我已按 ").append(normalizeMode(mode)).append(" 模式处理你的问题：");
        answer.append(latestUserMessage.length() > 160 ? latestUserMessage.substring(0, 160) + "..." : latestUserMessage);
        answer.append("\n\n");
        answer.append("演示要点：TeamFlow AI 已完成 JWT 认证、RBAC 动态菜单、项目协作、任务看板、知识库、文件中心和 AI 聊天的模块化闭环。");
        if (!references.isEmpty()) {
            answer.append("\n\n引用来源：");
            for (int i = 0; i < references.size(); i++) {
                AiReferenceItem reference = references.get(i);
                answer.append("\n").append(i + 1).append(". ").append(reference.title()).append("：").append(reference.snippet());
            }
        }
        return new AiProvider.AiAnswer(answer.toString(), Math.max(32, answer.length() / 4), "mock-ai", true);
    }

    public AiProvider.AiAgentAnswer chatWithTools(List<AiProvider.AiPromptMessage> messages, List<AiProvider.AiToolDefinition> tools) {
        String latestUserMessage = messages.stream()
                .filter(message -> "USER".equalsIgnoreCase(message.role()))
                .reduce((first, second) -> second)
                .map(AiProvider.AiPromptMessage::content)
                .orElse("");
        // 下面按关键词模拟 tool_call，让本地没有真实模型时也能走完整 Agent 编排链路。
        // 顺序从更具体的知识库/简报/项目查询到更宽泛的“创建任务”，避免“任务”关键字过早命中创建。
        if (hasTool(tools, "search_knowledge") && looksLikeKnowledgeSearch(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall(
                    "mock-call-search-knowledge",
                    "search_knowledge",
                    Map.of("query", latestUserMessage, "topK", 5)
            )), 32, "mock-ai", true);
        }
        if (hasTool(tools, "daily_business_brief") && looksLikeDailyBrief(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall(
                    "mock-call-daily-business-brief",
                    "daily_business_brief",
                    Map.of("limit", 200)
            )), 32, "mock-ai", true);
        }
        if (hasTool(tools, "query_project_summary") && looksLikeProjectSummary(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall(
                    "mock-call-project-summary",
                    "query_project_summary",
                    Map.of("keyword", inferTaskKeyword(latestUserMessage), "limit", 100)
            )), 32, "mock-ai", true);
        }
        if (hasTool(tools, "send_notification") && looksLikeReminder(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall(
                    "mock-call-send-notification",
                    "send_notification",
                    Map.of("keyword", inferTaskKeyword(latestUserMessage), "message", latestUserMessage)
            )), 32, "mock-ai", true);
        }
        if (hasTool(tools, "assign_task") && looksLikeAssign(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall(
                    "mock-call-assign-task",
                    "assign_task",
                    Map.of("keyword", inferTaskKeyword(latestUserMessage))
            )), 32, "mock-ai", true);
        }
        if (hasTool(tools, "update_task_status") && looksLikeStatusUpdate(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall(
                    "mock-call-update-task-status",
                    "update_task_status",
                    Map.of("keyword", inferTaskKeyword(latestUserMessage), "status", inferStatus(latestUserMessage))
            )), 32, "mock-ai", true);
        }
        if (hasTool(tools, "list_my_tasks") && looksLikeTaskQuery(latestUserMessage)) {
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall("mock-call-list-tasks", "list_my_tasks", Map.of("limit", 10))), 32, "mock-ai", true);
        }
        if (hasTool(tools, "create_task") && looksLikeCreateTask(latestUserMessage)) {
            Map<String, Object> arguments = Map.of(
                    "title", inferTaskTitle(latestUserMessage),
                    "description", latestUserMessage,
                    "priority", latestUserMessage.contains("高") || latestUserMessage.contains("紧急") ? "HIGH" : "MEDIUM"
            );
            return new AiProvider.AiAgentAnswer("", List.of(new AiProvider.AiToolCall("mock-call-create-task", "create_task", arguments)), 32, "mock-ai", true);
        }
        String content = "我可以帮你创建待办、查询任务、整理进展和检索知识库。请直接描述你要办理的事项。";
        return new AiProvider.AiAgentAnswer(content, List.of(), Math.max(16, content.length() / 4), "mock-ai", true);
    }

    private boolean hasTool(List<AiProvider.AiToolDefinition> tools, String name) {
        return tools.stream().anyMatch(tool -> name.equals(tool.name()));
    }

    private boolean looksLikeCreateTask(String message) {
        return message.contains("创建") || message.contains("安排") || message.contains("建一个")
                || message.contains("任务") || message.contains("待办");
    }

    private boolean looksLikeTaskQuery(String message) {
        return message.contains("哪些任务") || message.contains("我的任务") || message.contains("待办")
                || message.contains("今天要做") || message.contains("逾期");
    }

    private boolean looksLikeStatusUpdate(String message) {
        return message.contains("完成") || message.contains("开始") || message.contains("进行中")
                || message.contains("测试") || message.contains("关闭") || message.contains("状态");
    }

    private boolean looksLikeAssign(String message) {
        return message.contains("指派") || message.contains("分配") || message.contains("负责人")
                || message.contains("交给");
    }

    private boolean looksLikeReminder(String message) {
        return message.contains("催") || message.contains("提醒") || message.contains("通知")
                || message.contains("跟进");
    }

    private boolean looksLikeProjectSummary(String message) {
        return message.contains("进展") || message.contains("简报") || message.contains("汇总")
                || message.contains("统计") || message.contains("项目情况");
    }

    private boolean looksLikeDailyBrief(String message) {
        return message.contains("老板") || message.contains("经营简报") || message.contains("每日简报")
                || message.contains("今日经营");
    }

    private boolean looksLikeKnowledgeSearch(String message) {
        return message.contains("知识库") || message.contains("制度") || message.contains("流程")
                || message.contains("规范") || message.contains("资料");
    }

    private String inferTaskTitle(String message) {
        String normalized = message.replace("帮我", "").replace("创建", "").replace("一个", "")
                .replace("待办", "").replace("任务", "").trim();
        if (normalized.isBlank()) {
            return "AI 创建的待办事项";
        }
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    private String inferTaskKeyword(String message) {
        // 从自然语言里粗略剔除动词，给任务搜索留一个可用关键词。
        // 真实模型会返回更可靠的 taskId/taskNo/keyword，Mock 只是演示兜底。
        String normalized = message.replace("帮我", "").replace("请", "").replace("任务", "")
                .replace("待办", "").replace("状态", "").replace("更新", "")
                .replace("完成", "").replace("催办", "").replace("提醒", "")
                .replace("进展", "").replace("简报", "").trim();
        if (normalized.isBlank()) {
            return message.length() > 40 ? message.substring(0, 40) : message;
        }
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    private String inferStatus(String message) {
        if (message.contains("测试") || message.contains("验收")) return "TESTING";
        if (message.contains("完成") || message.contains("已完成") || message.contains("办完")) return "DONE";
        if (message.contains("关闭") || message.contains("取消")) return "CLOSED";
        if (message.contains("开始") || message.contains("进行中")) return "DOING";
        return "TODO";
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "CHAT";
        }
        return mode.toUpperCase();
    }
}

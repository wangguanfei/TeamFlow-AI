package com.teamflow.ai.modules.ai.provider;

import com.teamflow.ai.modules.ai.dto.AiReferenceItem;
import org.springframework.stereotype.Component;

import java.util.List;

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

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "CHAT";
        }
        return mode.toUpperCase();
    }
}

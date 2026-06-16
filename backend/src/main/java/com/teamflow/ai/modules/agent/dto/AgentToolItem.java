package com.teamflow.ai.modules.agent.dto;

public record AgentToolItem(
        String name,
        String label,
        String description,
        boolean write,
        String requiredPermission
) {
}

package com.teamflow.ai.modules.agent.tool;

import java.util.Map;

public record ToolDefinition(
        String name,
        String label,
        String description,
        Map<String, Object> parameters,
        boolean write,
        String requiredPermission
) {
}

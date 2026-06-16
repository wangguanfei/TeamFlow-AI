package com.teamflow.ai.modules.agent.tool;

import com.teamflow.ai.common.security.UserPrincipal;

import java.util.Map;

public interface AgentTool {

    ToolDefinition definition();

    default Map<String, Object> preview(Map<String, Object> arguments, UserPrincipal user) {
        return Map.of("tool", definition().label(), "arguments", arguments == null ? Map.of() : arguments);
    }

    ToolResult execute(Map<String, Object> arguments, UserPrincipal user);
}

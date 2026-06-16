package com.teamflow.ai.modules.agent.tool;

import java.util.Map;

public record ToolResult(
        boolean success,
        String summary,
        Map<String, Object> data,
        String targetType,
        Long targetId,
        String url
) {
    public static ToolResult ok(String summary, Map<String, Object> data, String targetType, Long targetId, String url) {
        return new ToolResult(true, summary, data == null ? Map.of() : data, targetType, targetId, url);
    }
}

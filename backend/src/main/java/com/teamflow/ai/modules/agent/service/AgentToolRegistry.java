package com.teamflow.ai.modules.agent.service;

import com.teamflow.ai.modules.agent.dto.AgentToolItem;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolDefinition;
import com.teamflow.ai.modules.ai.provider.AiProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> mapped = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            mapped.put(tool.definition().name(), tool);
        }
        this.tools = Map.copyOf(mapped);
    }

    public AgentTool getRequired(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未知 Agent 工具: " + name);
        }
        return tool;
    }

    public List<AgentToolItem> list() {
        return tools.values().stream()
                .map(tool -> {
                    ToolDefinition definition = tool.definition();
                    return new AgentToolItem(
                            definition.name(),
                            definition.label(),
                            definition.description(),
                            definition.write(),
                            definition.requiredPermission()
                    );
                })
                .toList();
    }

    public List<AiProvider.AiToolDefinition> providerDefinitions() {
        return tools.values().stream()
                .map(tool -> {
                    ToolDefinition definition = tool.definition();
                    return new AiProvider.AiToolDefinition(
                            definition.name(),
                            definition.description(),
                            definition.parameters()
                    );
                })
                .toList();
    }
}

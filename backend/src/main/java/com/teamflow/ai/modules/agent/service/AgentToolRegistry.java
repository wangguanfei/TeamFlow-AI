package com.teamflow.ai.modules.agent.service;

import com.teamflow.ai.modules.agent.dto.AgentToolItem;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolDefinition;
import com.teamflow.ai.modules.ai.provider.AiProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具注册表。
 *
 * <p>Spring 会自动注入所有 AgentTool bean，这里按 definition.name() 建立不可变索引。
 * 同一份定义既服务后台“工具列表”页面，也会转换成 OpenAI-compatible tool schema 发给模型。</p>
 */
@Service
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> mapped = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            // 后注册同名工具会覆盖前者，因此新增工具时 name 必须全局唯一。
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
                    // Provider 不需要知道权限和 write 标记，只需要模型可见的 function name/description/schema。
                    return new AiProvider.AiToolDefinition(
                            definition.name(),
                            definition.description(),
                            definition.parameters()
                    );
                })
                .toList();
    }
}

package com.teamflow.ai.modules.agent.tool;

import com.teamflow.ai.common.security.UserPrincipal;

import java.util.Map;

/**
 * Agent 可调用工具的统一契约。
 *
 * <p>definition() 暴露给模型作为 function/tool schema；execute() 才是真正执行业务动作。
 * 对于 write=true 的工具，AgentOrchestrator 会先调用 preview() 生成确认卡片，
 * 等用户确认后才调用 execute()，新增写工具时不能在 preview() 中修改数据库。</p>
 */
public interface AgentTool {

    /** 返回模型可见的工具名称、描述、参数 JSON Schema、是否写操作以及所需权限。 */
    ToolDefinition definition();

    /** 写工具的预览内容，默认只回显参数；复杂工具应覆盖为面向用户的中文字段。 */
    default Map<String, Object> preview(Map<String, Object> arguments, UserPrincipal user) {
        return Map.of("tool", definition().label(), "arguments", arguments == null ? Map.of() : arguments);
    }

    /** 真正执行工具逻辑；调用前由 AgentToolExecutor 做当前用户权限校验。 */
    ToolResult execute(Map<String, Object> arguments, UserPrincipal user);
}

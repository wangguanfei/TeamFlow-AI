package com.teamflow.ai.modules.agent.service;

import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolResult;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Agent 工具执行门面。
 *
 * <p>所有 preview/execute 都从这里进入，保证权限校验不会散落在各个工具实现里。
 * 工具内部仍可以做业务级校验，例如任务是否存在、负责人是否唯一。</p>
 */
@Service
public class AgentToolExecutor {

    public Map<String, Object> preview(AgentTool tool, Map<String, Object> arguments, UserPrincipal user) {
        ensurePermission(tool, user);
        return tool.preview(arguments, user);
    }

    public ToolResult execute(AgentTool tool, Map<String, Object> arguments, UserPrincipal user) {
        ensurePermission(tool, user);
        return tool.execute(arguments, user);
    }

    private void ensurePermission(AgentTool tool, UserPrincipal user) {
        String required = tool.definition().requiredPermission();
        if (required == null || required.isBlank()) {
            return;
        }
        // 这里校验的是“当前登录用户能否做这件事”，不是模型权限；
        // 模型永远不能越过用户自己的 RBAC 权限执行后台动作。
        boolean allowed = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::equals);
        if (!allowed) {
            throw new BusinessException(403, "AI 企业助理没有权限代你执行该操作：" + required);
        }
    }
}

package com.teamflow.ai.modules.agent.service;

import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolResult;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Map;

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
        boolean allowed = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::equals);
        if (!allowed) {
            throw new BusinessException(403, "AI 企业助理没有权限代你执行该操作：" + required);
        }
    }
}

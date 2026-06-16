package com.teamflow.ai.modules.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.modules.agent.entity.AiAgentAction;
import com.teamflow.ai.modules.agent.mapper.AiAgentActionMapper;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AgentActionService {

    private final AiAgentActionMapper actionMapper;
    private final ObjectMapper objectMapper;

    public AgentActionService(AiAgentActionMapper actionMapper, ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.objectMapper = objectMapper;
    }

    public AiAgentAction create(Long sessionId, Long messageId, Long userId, AgentTool tool,
                                Map<String, Object> arguments, Map<String, Object> preview, String status) {
        AiAgentAction action = new AiAgentAction();
        action.setSessionId(sessionId);
        action.setMessageId(messageId);
        action.setUserId(userId);
        action.setToolName(tool.definition().name());
        action.setToolLabel(tool.definition().label());
        action.setArgumentsJson(writeJson(arguments));
        action.setPreviewJson(writeJson(preview));
        action.setIsWrite(tool.definition().write() ? 1 : 0);
        action.setStatus(status);
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        actionMapper.insert(action);
        return action;
    }

    public void updateTokenHash(Long actionId, String hash) {
        AiAgentAction action = actionMapper.selectById(actionId);
        if (action == null) return;
        action.setConfirmTokenHash(hash);
        action.setUpdatedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    public void markExecuted(Long actionId, Long confirmedBy, ToolResult result, long durationMs) {
        AiAgentAction action = actionMapper.selectById(actionId);
        if (action == null) return;
        action.setStatus("EXECUTED");
        action.setConfirmedBy(confirmedBy);
        action.setConfirmedAt(LocalDateTime.now());
        action.setResultJson(writeJson(result.data()));
        action.setTargetType(result.targetType());
        action.setTargetId(result.targetId());
        action.setDurationMs(durationMs);
        action.setUpdatedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    public void markFailed(Long actionId, String message, long durationMs) {
        AiAgentAction action = actionMapper.selectById(actionId);
        if (action == null) return;
        action.setStatus("FAILED");
        action.setErrorMessage(message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message));
        action.setDurationMs(durationMs);
        action.setUpdatedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    public void markCancelled(Long actionId, Long userId) {
        AiAgentAction action = actionMapper.selectById(actionId);
        if (action == null) return;
        action.setStatus("CANCELLED");
        action.setConfirmedBy(userId);
        action.setConfirmedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"json serialization failed\"}";
        }
    }
}

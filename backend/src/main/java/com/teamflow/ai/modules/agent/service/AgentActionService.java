package com.teamflow.ai.modules.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.agent.dto.AgentActionPageItem;
import com.teamflow.ai.modules.agent.entity.AiAgentAction;
import com.teamflow.ai.modules.agent.mapper.AiAgentActionMapper;
import com.teamflow.ai.modules.agent.tool.AgentTool;
import com.teamflow.ai.modules.agent.tool.ToolResult;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentActionService {

    private final AiAgentActionMapper actionMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AgentActionService(AiAgentActionMapper actionMapper,
                              SysUserMapper userMapper,
                              ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public PageResult<AgentActionPageItem> page(long pageNo, long pageSize,
                                                String username, String toolName, String status,
                                                Integer isWrite,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AiAgentAction> wrapper = new LambdaQueryWrapper<>();

        if (username != null && !username.isBlank()) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .select(SysUser::getId)
                            .and(q -> q.like(SysUser::getUsername, username)
                                    .or()
                                    .like(SysUser::getNickname, username)))
                    .stream()
                    .map(SysUser::getId)
                    .toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(
                        PageRequestUtils.normalizePage(pageNo),
                        PageRequestUtils.normalizeSize(pageSize),
                        0,
                        List.of()
                );
            }
            wrapper.in(AiAgentAction::getUserId, userIds);
        }

        if (toolName != null && !toolName.isBlank()) {
            wrapper.and(q -> q.like(AiAgentAction::getToolName, toolName)
                    .or()
                    .like(AiAgentAction::getToolLabel, toolName));
        }

        wrapper.eq(status != null && !status.isBlank(), AiAgentAction::getStatus, status)
                .eq(isWrite != null, AiAgentAction::getIsWrite, isWrite)
                .ge(startTime != null, AiAgentAction::getCreatedAt, startTime)
                .le(endTime != null, AiAgentAction::getCreatedAt, endTime)
                .orderByDesc(AiAgentAction::getCreatedAt);

        Page<AiAgentAction> result = actionMapper.selectPage(PageRequestUtils.of(pageNo, pageSize), wrapper);
        Map<Long, String> usernameMap = loadUsernameMap(result.getRecords());
        List<AgentActionPageItem> items = result.getRecords().stream()
                .map(action -> toPageItem(action, usernameMap))
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), items);
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

    private Map<Long, String> loadUsernameMap(List<AiAgentAction> actions) {
        Set<Long> ids = new HashSet<>();
        for (AiAgentAction action : actions) {
            if (action.getUserId() != null) ids.add(action.getUserId());
            if (action.getConfirmedBy() != null) ids.add(action.getConfirmedBy());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> displayUsername(entry.getValue())));
    }

    private String displayUsername(SysUser user) {
        if (user == null) {
            return null;
        }
        if (user.getNickname() == null || user.getNickname().isBlank() || user.getNickname().equals(user.getUsername())) {
            return user.getUsername();
        }
        return user.getNickname() + " (" + user.getUsername() + ")";
    }

    private AgentActionPageItem toPageItem(AiAgentAction action, Map<Long, String> usernameMap) {
        return new AgentActionPageItem(
                action.getId(),
                action.getSessionId(),
                action.getMessageId(),
                action.getUserId(),
                usernameMap.get(action.getUserId()),
                action.getConfirmedBy(),
                usernameMap.get(action.getConfirmedBy()),
                action.getToolName(),
                action.getToolLabel(),
                action.getArgumentsJson(),
                action.getPreviewJson(),
                action.getResultJson(),
                action.getIsWrite(),
                action.getStatus(),
                action.getConfirmedAt(),
                action.getTargetType(),
                action.getTargetId(),
                action.getErrorMessage(),
                action.getDurationMs(),
                action.getCreatedAt(),
                action.getUpdatedAt()
        );
    }
}

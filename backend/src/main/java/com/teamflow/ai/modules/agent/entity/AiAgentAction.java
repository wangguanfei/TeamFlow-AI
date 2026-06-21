package com.teamflow.ai.modules.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_agent_action")
public class AiAgentAction {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 AI 会话，便于从聊天历史追溯工具调用。 */
    private Long sessionId;
    /** 触发本次工具调用的用户消息。 */
    private Long messageId;
    private Long userId;
    private String toolName;
    private String toolLabel;
    /** 模型返回的原始工具参数 JSON。 */
    private String argumentsJson;
    /** 写操作确认前展示给用户的预览 JSON。 */
    private String previewJson;
    /** 工具执行完成后的结构化结果 JSON。 */
    private String resultJson;
    /** 1 表示写操作，需要用户确认；0 表示只读工具。 */
    private Integer isWrite;
    /** PENDING/RUNNING/EXECUTED/CANCELLED/FAILED。 */
    private String status;
    /** confirmToken 的 SHA-256 hash，数据库不保存明文 token。 */
    private String confirmTokenHash;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String targetType;
    private Long targetId;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolLabel() { return toolLabel; }
    public void setToolLabel(String toolLabel) { this.toolLabel = toolLabel; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    public String getPreviewJson() { return previewJson; }
    public void setPreviewJson(String previewJson) { this.previewJson = previewJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Integer getIsWrite() { return isWrite; }
    public void setIsWrite(Integer isWrite) { this.isWrite = isWrite; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfirmTokenHash() { return confirmTokenHash; }
    public void setConfirmTokenHash(String confirmTokenHash) { this.confirmTokenHash = confirmTokenHash; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

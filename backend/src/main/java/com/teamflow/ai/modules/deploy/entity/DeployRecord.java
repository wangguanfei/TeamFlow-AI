package com.teamflow.ai.modules.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("deploy_record")
public class DeployRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String target;
    private Boolean skipPull;
    private String status;
    private Long triggerUserId;
    private String triggerUsername;
    private Integer exitCode;
    private String logFile;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long costMs;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Boolean getSkipPull() { return skipPull; }
    public void setSkipPull(Boolean skipPull) { this.skipPull = skipPull; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTriggerUserId() { return triggerUserId; }
    public void setTriggerUserId(Long triggerUserId) { this.triggerUserId = triggerUserId; }
    public String getTriggerUsername() { return triggerUsername; }
    public void setTriggerUsername(String triggerUsername) { this.triggerUsername = triggerUsername; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getLogFile() { return logFile; }
    public void setLogFile(String logFile) { this.logFile = logFile; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

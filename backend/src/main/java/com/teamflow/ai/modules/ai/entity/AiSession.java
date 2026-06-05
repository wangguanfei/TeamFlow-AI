package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_session")
public class AiSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long spaceId;
    private String sessionTitle;
    private String modelName;
    private String sessionType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

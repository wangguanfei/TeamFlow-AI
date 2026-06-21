package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * RAG 索引异步任务，对应数据库表 ai_index_job。
 * <p>
 * 文档发布/删除时不直接调用 embedding-service，而是写一条 PENDING 任务到本表，
 * 由定时 worker（{@code processNextIndexJob}）轮询执行，实现异步解耦。
 * 失败任务会按指数退避重试（由 attempts 字段控制），避免暂时性故障导致数据丢失。
 */
@TableName("ai_index_job")
public class AiIndexJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 待索引或待删除的知识文档。 */
    private Long docId;
    /** REBUILD 或 DELETE。 */
    private String actionType;
    /** PENDING/RUNNING/DONE/FAILED。 */
    private String jobStatus;
    /** 已尝试次数，失败任务会按退避窗口重试。 */
    private Integer attempts;
    private String errorMessage;
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getJobStatus() { return jobStatus; }
    public void setJobStatus(String jobStatus) { this.jobStatus = jobStatus; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

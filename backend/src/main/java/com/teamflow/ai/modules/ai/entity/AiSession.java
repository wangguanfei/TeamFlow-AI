package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 会话实体，对应数据库表 ai_session。
 * <p>
 * 每条记录代表一个用户与 AI 的独立对话上下文。会话维护了模型选型、
 * 知识空间绑定等元信息，聊天消息通过 {@link AiMessage} 关联到会话。
 * 删除采用软删除（deleted=1），前端侧边栏只展示 deleted=0 的记录。
 */
@TableName("ai_session")
public class AiSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话所属用户，所有前台查询都按它隔离个人 AI 历史。 */
    private Long userId;
    /** 关联知识空间；为空表示不限空间或普通大模型直答模式。 */
    private Long spaceId;
    /** 会话标题，首次发消息时由 AI 自动生成摘要，或由用户手动修改。 */
    private String sessionTitle;
    /** 实际使用的模型名称，Mock 模式会写 mock-ai。 */
    private String modelName;
    /**
     * 会话类型，控制 system prompt 风格：
     * CHAT-通用对话、AGENT-工具调用、SUMMARY-摘要、CODE-代码、SQL-数据库问答。
     * 历史遗留值 KNOWLEDGE 已被 useKnowledge 字段替代，不再新建。
     */
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

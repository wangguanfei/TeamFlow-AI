package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 消息实体，对应数据库表 ai_message。
 * <p>
 * 记录一次 AI 对话中的单条消息（用户问或 AI 答）。
 * 每条消息不可修改（无 updatedAt），保证对话历史的可追溯性。
 * RAG 引用信息以 JSON 快照形式存储，不随知识库变更而改变，确保历史回溯一致性。
 */
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属会话 ID，关联 ai_session。 */
    private Long sessionId;
    /**
     * 消息角色：user（用户输入）、assistant（AI 回复）、system（系统 prompt，一般不持久化）。
     * 遵循 OpenAI Messages API 的 role 规范。
     */
    private String role;
    /** 消息正文，assistant 消息为 AI 生成的完整回复（流式结束后写入）。 */
    private String content;
    /** 本条消息消耗的 token 数，用于统计和配额控制（当前为估算值）。 */
    private Integer tokens;
    /**
     * RAG 检索到的知识引用列表，序列化为 JSON 数组存储。
     * 结构为 {@code List<AiReferenceItem>}，包含文档 ID、标题、相关片段等。
     * 存快照而非外键，避免知识库文档更新后引用内容与历史对话不一致。
     */
    private String referencesJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }
    public String getReferencesJson() { return referencesJson; }
    public void setReferencesJson(String referencesJson) { this.referencesJson = referencesJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 消息反馈实体，对应数据库表 ai_message_feedback。
 * <p>
 * 用于收集用户对 AI 回答质量的评价，可用于后续模型效果分析和 RAG 优化。
 * 每条 AI 消息（assistant role）允许被点赞/踩，并填写详细原因。
 */
@TableName("ai_message_feedback")
public class AiMessageFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 被评价的 AI 消息 ID，对应 ai_message 表。 */
    private Long messageId;
    /** 提交反馈的用户 ID。 */
    private Long userId;
    /** 评分，1-5 分（1=很差，5=很好）；前端通常映射为点赞/踩两个档位。 */
    private Integer rating;
    /** 不满意的简短原因标签，如"答非所问"、"引用错误"等。 */
    private String reason;
    /** 用户认为正确的文档 ID，辅助 RAG 召回质量分析。 */
    private Long expectedDocId;
    /** 用户的详细评论，自由文本。 */
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getExpectedDocId() { return expectedDocId; }
    public void setExpectedDocId(Long expectedDocId) { this.expectedDocId = expectedDocId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

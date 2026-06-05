package com.teamflow.ai.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_tag")
public class KnowledgeTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String tagName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

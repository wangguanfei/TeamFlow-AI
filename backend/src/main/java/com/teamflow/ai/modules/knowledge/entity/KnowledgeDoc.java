package com.teamflow.ai.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private Long parentId;
    private String title;
    private String contentMd;
    private String contentText;
    private Long authorId;
    private String docStatus;
    private Integer sortNo;
    private Integer versionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContentMd() { return contentMd; }
    public void setContentMd(String contentMd) { this.contentMd = contentMd; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getDocStatus() { return docStatus; }
    public void setDocStatus(String docStatus) { this.docStatus = docStatus; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_embedding")
public class AiEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String chunkText;
    private String embeddingHash;
    private String embeddingText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    public String getEmbeddingHash() { return embeddingHash; }
    public void setEmbeddingHash(String embeddingHash) { this.embeddingHash = embeddingHash; }
    public String getEmbeddingText() { return embeddingText; }
    public void setEmbeddingText(String embeddingText) { this.embeddingText = embeddingText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

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
    private String embeddingModel;
    private Integer embeddingDim;
    private String vectorPointId;
    private String contentHash;
    private String indexStatus;
    private LocalDateTime indexedAt;
    private String indexError;
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
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDim() { return embeddingDim; }
    public void setEmbeddingDim(Integer embeddingDim) { this.embeddingDim = embeddingDim; }
    public String getVectorPointId() { return vectorPointId; }
    public void setVectorPointId(String vectorPointId) { this.vectorPointId = vectorPointId; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }
    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }
    public String getIndexError() { return indexError; }
    public void setIndexError(String indexError) { this.indexError = indexError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

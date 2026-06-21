package com.teamflow.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 向量嵌入记录，对应数据库表 ai_embedding。
 * <p>
 * 每条记录代表一个知识文档切片的向量化结果。文档在发布时会被切分为多个 chunk，
 * 每个 chunk 由 embedding-service 转换为 512 维向量后存入 Qdrant；本表作为
 * MySQL 侧的索引元数据，用于状态管理（PENDING/READY/FAILED/MANUAL）、
 * 去重（contentHash）和 Qdrant 向量的回溯删除（vectorPointId）。
 * <p>
 * 真实向量数据存储在 Qdrant，本表不存向量值。
 */
@TableName("ai_embedding")
public class AiEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 来源知识文档 ID。 */
    private Long docId;
    /** 文档切片序号，从 0 开始。 */
    private Integer chunkIndex;
    /** 实际送入 embedding 模型的文本片段。 */
    private String chunkText;
    /** 切片内容 hash，用于判断同一版本内容是否变化。 */
    private String embeddingHash;
    /** 早期伪向量/调试字段；真实向量存储在 Qdrant。 */
    private String embeddingText;
    private String embeddingModel;
    private Integer embeddingDim;
    /** Qdrant point id，用于回溯和删除向量。 */
    private String vectorPointId;
    private String contentHash;
    /** PENDING/READY/FAILED/MANUAL，用于后台查看索引状态。 */
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

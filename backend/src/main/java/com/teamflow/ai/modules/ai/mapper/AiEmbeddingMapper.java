package com.teamflow.ai.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.ai.modules.ai.entity.AiEmbedding;

/**
 * AI 向量嵌入记录数据访问层，对应 ai_embedding 表。
 * 继承 MyBatis-Plus BaseMapper，复杂索引查询（按状态、按文档批量操作）在 AiKnowledgeIndexService 中用 LambdaQueryWrapper 构建。
 */
public interface AiEmbeddingMapper extends BaseMapper<AiEmbedding> {
}

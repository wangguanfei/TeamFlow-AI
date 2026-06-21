package com.teamflow.ai.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.ai.modules.ai.entity.AiIndexJob;

/**
 * RAG 索引任务数据访问层，对应 ai_index_job 表。
 * 继承 MyBatis-Plus BaseMapper，Worker 轮询查询（PENDING/FAILED 任务选取）
 * 在 AiKnowledgeIndexService.processNextIndexJob() 中用 LambdaQueryWrapper 构建。
 */
public interface AiIndexJobMapper extends BaseMapper<AiIndexJob> {
}

package com.teamflow.ai.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.ai.modules.ai.entity.AiSession;

/**
 * AI 会话数据访问层，对应 ai_session 表。
 * 继承 MyBatis-Plus BaseMapper，提供标准 CRUD 操作，无自定义 SQL。
 * 软删除过滤（deleted=0）由 MyBatis-Plus 全局配置自动注入。
 */
public interface AiSessionMapper extends BaseMapper<AiSession> {
}

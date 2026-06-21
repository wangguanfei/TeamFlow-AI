package com.teamflow.ai.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.ai.modules.ai.entity.AiMessage;

/**
 * AI 消息数据访问层，对应 ai_message 表。
 * 继承 MyBatis-Plus BaseMapper，消息无软删除，物理删除由 removeById/removeByIds 执行。
 */
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}

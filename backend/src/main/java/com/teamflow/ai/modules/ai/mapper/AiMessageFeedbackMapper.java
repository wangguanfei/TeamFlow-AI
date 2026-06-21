package com.teamflow.ai.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.ai.modules.ai.entity.AiMessageFeedback;

/**
 * AI 消息反馈数据访问层，对应 ai_message_feedback 表。
 * 继承 MyBatis-Plus BaseMapper，upsert 逻辑（每条消息只保留一条用户反馈）在服务层实现。
 */
public interface AiMessageFeedbackMapper extends BaseMapper<AiMessageFeedback> {
}

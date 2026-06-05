package com.teamflow.ai.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.ai.modules.task.entity.TaskExecutor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskExecutorMapper extends BaseMapper<TaskExecutor> {
}

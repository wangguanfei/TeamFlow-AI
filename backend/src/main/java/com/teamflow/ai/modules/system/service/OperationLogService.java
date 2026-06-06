package com.teamflow.ai.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.OperationLogPageItem;
import com.teamflow.ai.modules.system.entity.OperationLog;
import com.teamflow.ai.modules.system.mapper.OperationLogMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** 异步写入，独立事务，不影响主链路回滚 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asyncSave(OperationLog log) {
        operationLogMapper.insert(log);
    }

    public PageResult<OperationLogPageItem> page(long pageNo, long pageSize,
                                                  String username, String moduleName, String operationType,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .like(username != null && !username.isBlank(), OperationLog::getUsername, username)
                .like(moduleName != null && !moduleName.isBlank(), OperationLog::getModuleName, moduleName)
                .eq(operationType != null && !operationType.isBlank(), OperationLog::getOperationType, operationType)
                .ge(startTime != null, OperationLog::getCreatedAt, startTime)
                .le(endTime != null, OperationLog::getCreatedAt, endTime)
                .orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<OperationLogPageItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        operationLogMapper.deleteBatchIds(ids);
    }

    private OperationLogPageItem toItem(OperationLog e) {
        return new OperationLogPageItem(
                e.getId(), e.getUserId(), e.getUsername(),
                e.getModuleName(), e.getOperationType(),
                e.getRequestMethod(), e.getRequestUri(), e.getRequestParams(),
                e.getResponseStatus(), e.getErrorMessage(),
                e.getCostMs(), e.getClientIp(), e.getCreatedAt()
        );
    }
}

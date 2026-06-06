package com.teamflow.ai.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.LoginLogCleanRequest;
import com.teamflow.ai.modules.system.dto.LoginLogPageItem;
import com.teamflow.ai.modules.system.entity.LoginLog;
import com.teamflow.ai.modules.system.mapper.LoginLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public PageResult<LoginLogPageItem> page(long pageNo, long pageSize,
                                             String username, Integer status,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .like(username != null && !username.isBlank(), LoginLog::getUsername, username)
                .eq(status != null, LoginLog::getStatus, status)
                .ge(startTime != null, LoginLog::getCreatedAt, startTime)
                .le(endTime != null, LoginLog::getCreatedAt, endTime)
                .orderByDesc(LoginLog::getCreatedAt);

        Page<LoginLog> result = loginLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<LoginLogPageItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        loginLogMapper.deleteBatchIds(ids);
    }

    public long clean(LoginLogCleanRequest request) {
        LocalDateTime before = LocalDateTime.now().minusDays(request.beforeDays());
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, before);
        return loginLogMapper.delete(wrapper);
    }

    private LoginLogPageItem toItem(LoginLog e) {
        return new LoginLogPageItem(
                e.getId(), e.getUserId(), e.getUsername(),
                e.getLoginIp(), e.getLoginLocation(), e.getBrowser(), e.getOs(),
                e.getUserAgent(), e.getStatus(), e.getMessage(), e.getCreatedAt()
        );
    }
}

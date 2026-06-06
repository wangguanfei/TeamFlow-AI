package com.teamflow.ai.modules.system.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.IdListRequest;
import com.teamflow.ai.modules.system.dto.LoginLogCleanRequest;
import com.teamflow.ai.modules.system.dto.LoginLogPageItem;
import com.teamflow.ai.modules.system.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "登录日志")
@RestController
@RequestMapping("/api/login-logs")
public class LoginLogController {

    private final LoginLogService loginLogService;

    public LoginLogController(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @Operation(summary = "分页查询登录日志")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:loginlog:view')")
    public ApiResult<PageResult<LoginLogPageItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        return ApiResult.success(loginLogService.page(page, size, username, status, startTime, endTime));
    }

    @Operation(summary = "批量删除登录日志")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('system:loginlog:delete')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        loginLogService.batchDelete(request.ids());
        return ApiResult.success();
    }

    @Operation(summary = "清理历史登录日志")
    @PostMapping("/clean")
    @PreAuthorize("hasAuthority('system:loginlog:delete')")
    public ApiResult<Long> clean(@Valid @RequestBody LoginLogCleanRequest request) {
        return ApiResult.success(loginLogService.clean(request));
    }
}

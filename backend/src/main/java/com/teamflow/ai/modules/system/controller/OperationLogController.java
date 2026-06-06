package com.teamflow.ai.modules.system.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.IdListRequest;
import com.teamflow.ai.modules.system.dto.OperationLogPageItem;
import com.teamflow.ai.modules.system.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "分页查询操作日志")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:operlog:view')")
    public ApiResult<PageResult<OperationLogPageItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        return ApiResult.success(
                operationLogService.page(page, size, username, moduleName, operationType, startTime, endTime)
        );
    }

    @Operation(summary = "批量删除操作日志")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('system:operlog:delete')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        operationLogService.batchDelete(request.ids());
        return ApiResult.success();
    }
}

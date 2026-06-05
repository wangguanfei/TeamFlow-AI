package com.teamflow.ai.modules.dashboard.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.modules.dashboard.dto.ChartPoint;
import com.teamflow.ai.modules.dashboard.dto.DashboardOverview;
import com.teamflow.ai.modules.dashboard.dto.DashboardTodoItem;
import com.teamflow.ai.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "工作台")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "工作台总览统计")
    @GetMapping("/overview")
    public ApiResult<DashboardOverview> overview() {
        return ApiResult.success(dashboardService.overview());
    }

    @Operation(summary = "项目趋势图")
    @GetMapping("/project-trend")
    public ApiResult<List<ChartPoint>> projectTrend() {
        return ApiResult.success(dashboardService.projectTrend());
    }

    @Operation(summary = "成员活跃度")
    @GetMapping("/member-active")
    public ApiResult<List<ChartPoint>> memberActive() {
        return ApiResult.success(dashboardService.memberActive());
    }

    @Operation(summary = "AI使用统计")
    @GetMapping("/ai-usage")
    public ApiResult<List<ChartPoint>> aiUsage() {
        return ApiResult.success(dashboardService.aiUsage());
    }

    @Operation(summary = "当前待办任务")
    @GetMapping("/todos")
    public ApiResult<List<DashboardTodoItem>> todos() {
        return ApiResult.success(dashboardService.currentTodos());
    }
}

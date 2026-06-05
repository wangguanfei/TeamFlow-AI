package com.teamflow.ai.modules.task.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.task.dto.TaskWorklogItem;
import com.teamflow.ai.modules.task.dto.TaskWorklogRequest;
import com.teamflow.ai.modules.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "任务工时")
@RestController
@RequestMapping("/api/task-worklogs")
public class TaskWorklogController {

    private final TaskService taskService;

    public TaskWorklogController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "创建任务工时")
    @PostMapping
    @PreAuthorize("hasAuthority('task:worklog')")
    public ApiResult<TaskWorklogItem> create(@Valid @RequestBody TaskWorklogRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(taskService.createWorklog(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询任务工时")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<PageResult<TaskWorklogItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(taskService.pageWorklogs(page, size, taskId, keyword));
    }

    @Operation(summary = "删除任务工时")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:worklog')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskService.deleteWorklog(id);
        return ApiResult.success();
    }
}

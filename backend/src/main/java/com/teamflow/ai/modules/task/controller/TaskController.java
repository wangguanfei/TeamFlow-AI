package com.teamflow.ai.modules.task.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.task.dto.GanttTaskItem;
import com.teamflow.ai.modules.task.dto.KanbanColumn;
import com.teamflow.ai.modules.task.dto.TaskDetail;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.dto.TaskRequest;
import com.teamflow.ai.modules.task.dto.TaskStatusRequest;
import com.teamflow.ai.modules.task.service.TaskService;
import com.teamflow.ai.common.log.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "任务")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Log(module = "任务管理", type = "新增")
    @Operation(summary = "创建任务")
    @PostMapping
    @PreAuthorize("hasAuthority('task:create')")
    public ApiResult<TaskDetail> create(@Valid @RequestBody TaskRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(taskService.createTask(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<PageResult<TaskListItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(taskService.pageTasks(page, size, projectId, status, keyword));
    }

    @Operation(summary = "查询任务看板数据")
    @GetMapping("/kanban")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<List<KanbanColumn>> kanban(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(taskService.kanban(projectId, keyword));
    }

    @Operation(summary = "查询任务甘特图数据")
    @GetMapping("/gantt")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<List<GanttTaskItem>> gantt(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(taskService.gantt(projectId, keyword));
    }

    @Operation(summary = "详情查询任务")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<TaskDetail> detail(@PathVariable Long id) {
        return ApiResult.success(taskService.getTask(id));
    }

    @Log(module = "任务管理", type = "修改")
    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResult<TaskDetail> update(@PathVariable Long id,
                                        @Valid @RequestBody TaskRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(taskService.updateTask(id, request, principal.getUserId()));
    }

    @Log(module = "任务管理", type = "状态变更")
    @Operation(summary = "修改任务状态/看板拖拽")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResult<TaskListItem> updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest request) {
        return ApiResult.success(taskService.updateStatus(id, request));
    }

    @Log(module = "任务管理", type = "删除")
    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResult.success();
    }
}

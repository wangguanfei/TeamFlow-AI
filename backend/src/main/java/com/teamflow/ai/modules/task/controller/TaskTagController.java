package com.teamflow.ai.modules.task.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.task.dto.TaskTagItem;
import com.teamflow.ai.modules.task.dto.TaskTagRequest;
import com.teamflow.ai.modules.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "任务标签")
@RestController
@RequestMapping("/api/task-tags")
public class TaskTagController {

    private final TaskService taskService;

    public TaskTagController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "创建任务标签")
    @PostMapping
    @PreAuthorize("hasAuthority('task:tag')")
    public ApiResult<TaskTagItem> create(@Valid @RequestBody TaskTagRequest request) {
        return ApiResult.success(taskService.createTag(request));
    }

    @Operation(summary = "分页查询任务标签")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<PageResult<TaskTagItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(taskService.pageTags(page, size, taskId, keyword));
    }

    @Operation(summary = "删除任务标签")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:tag')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskService.deleteTag(id);
        return ApiResult.success();
    }
}

package com.teamflow.ai.modules.task.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.task.dto.TaskCommentItem;
import com.teamflow.ai.modules.task.dto.TaskCommentRequest;
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

@Tag(name = "任务评论")
@RestController
@RequestMapping("/api/task-comments")
public class TaskCommentController {

    private final TaskService taskService;

    public TaskCommentController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "创建任务评论")
    @PostMapping
    @PreAuthorize("hasAuthority('task:comment')")
    public ApiResult<TaskCommentItem> create(@Valid @RequestBody TaskCommentRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(taskService.createComment(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询任务评论")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<PageResult<TaskCommentItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(taskService.pageComments(page, size, taskId, keyword));
    }

    @Operation(summary = "删除任务评论")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:comment')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskService.deleteComment(id);
        return ApiResult.success();
    }
}

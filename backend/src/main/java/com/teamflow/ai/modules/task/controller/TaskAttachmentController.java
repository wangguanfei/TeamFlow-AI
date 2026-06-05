package com.teamflow.ai.modules.task.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.task.dto.TaskAttachmentItem;
import com.teamflow.ai.modules.task.dto.TaskAttachmentRequest;
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

@Tag(name = "任务附件")
@RestController
@RequestMapping("/api/task-attachments")
public class TaskAttachmentController {

    private final TaskService taskService;

    public TaskAttachmentController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "创建任务附件")
    @PostMapping
    @PreAuthorize("hasAuthority('task:attachment')")
    public ApiResult<TaskAttachmentItem> create(@Valid @RequestBody TaskAttachmentRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(taskService.createAttachment(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询任务附件")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResult<PageResult<TaskAttachmentItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long taskId
    ) {
        return ApiResult.success(taskService.pageAttachments(page, size, taskId));
    }

    @Operation(summary = "删除任务附件")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:attachment')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskService.deleteAttachment(id);
        return ApiResult.success();
    }
}

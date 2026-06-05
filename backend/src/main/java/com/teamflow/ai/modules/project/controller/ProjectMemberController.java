package com.teamflow.ai.modules.project.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.project.dto.ProjectMemberItem;
import com.teamflow.ai.modules.project.dto.ProjectMemberRequest;
import com.teamflow.ai.modules.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "项目成员")
@RestController
@RequestMapping("/api/project-members")
public class ProjectMemberController {

    private final ProjectService projectService;

    public ProjectMemberController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "创建项目成员")
    @PostMapping
    @PreAuthorize("hasAuthority('project:member')")
    public ApiResult<ProjectMemberItem> create(@Valid @RequestBody ProjectMemberRequest request) {
        return ApiResult.success(projectService.createMember(request));
    }

    @Operation(summary = "分页查询项目成员")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<PageResult<ProjectMemberItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(projectService.pageMembers(page, size, projectId, keyword));
    }

    @Operation(summary = "更新项目成员")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:member')")
    public ApiResult<ProjectMemberItem> update(@PathVariable Long id, @Valid @RequestBody ProjectMemberRequest request) {
        return ApiResult.success(projectService.updateMember(id, request));
    }

    @Operation(summary = "删除项目成员")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:member')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.deleteMember(id);
        return ApiResult.success();
    }
}

package com.teamflow.ai.modules.project.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.project.dto.ProjectDetail;
import com.teamflow.ai.modules.project.dto.ProjectListItem;
import com.teamflow.ai.modules.project.dto.ProjectRequest;
import com.teamflow.ai.modules.project.dto.ProjectStats;
import com.teamflow.ai.modules.project.service.ProjectService;
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

@Tag(name = "项目")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "创建项目")
    @PostMapping
    @PreAuthorize("hasAuthority('project:create')")
    public ApiResult<ProjectDetail> create(@Valid @RequestBody ProjectRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(projectService.createProject(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询项目")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<PageResult<ProjectListItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(projectService.pageProjects(page, size, keyword));
    }

    @Operation(summary = "项目统计")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<ProjectStats> stats() {
        return ApiResult.success(projectService.stats());
    }

    @Operation(summary = "详情查询项目")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<ProjectDetail> detail(@PathVariable Long id) {
        return ApiResult.success(projectService.getProject(id));
    }

    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public ApiResult<ProjectDetail> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ApiResult.success(projectService.updateProject(id, request));
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResult.success();
    }
}

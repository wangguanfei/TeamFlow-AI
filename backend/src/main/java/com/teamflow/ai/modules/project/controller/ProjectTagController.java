package com.teamflow.ai.modules.project.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.project.dto.ProjectTagItem;
import com.teamflow.ai.modules.project.dto.ProjectTagRequest;
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

@Tag(name = "项目标签")
@RestController
@RequestMapping("/api/project-tags")
public class ProjectTagController {

    private final ProjectService projectService;

    public ProjectTagController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "创建项目标签")
    @PostMapping
    @PreAuthorize("hasAuthority('project:tag')")
    public ApiResult<ProjectTagItem> create(@Valid @RequestBody ProjectTagRequest request) {
        return ApiResult.success(projectService.createTag(request));
    }

    @Operation(summary = "分页查询项目标签")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<PageResult<ProjectTagItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(projectService.pageTags(page, size, projectId, keyword));
    }

    @Operation(summary = "更新项目标签")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:tag')")
    public ApiResult<ProjectTagItem> update(@PathVariable Long id, @Valid @RequestBody ProjectTagRequest request) {
        return ApiResult.success(projectService.updateTag(id, request));
    }

    @Operation(summary = "删除项目标签")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:tag')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.deleteTag(id);
        return ApiResult.success();
    }
}

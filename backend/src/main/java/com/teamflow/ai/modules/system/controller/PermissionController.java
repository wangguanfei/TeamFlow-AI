package com.teamflow.ai.modules.system.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.IdListRequest;
import com.teamflow.ai.modules.system.dto.PermissionRequest;
import com.teamflow.ai.modules.system.entity.SysPermission;
import com.teamflow.ai.modules.system.service.RbacService;
import com.teamflow.ai.common.log.Log;
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

@Tag(name = "权限")
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final RbacService rbacService;

    public PermissionController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Log(module = "权限管理", type = "新增")
    @Operation(summary = "创建权限")
    @PostMapping
    @PreAuthorize("hasAuthority('system:permission:create')")
    public ApiResult<SysPermission> create(@Valid @RequestBody PermissionRequest request) {
        return ApiResult.success(rbacService.createPermission(request));
    }

    @Operation(summary = "分页查询权限")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:permission:view')")
    public ApiResult<PageResult<SysPermission>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(rbacService.pagePermissions(page, size, keyword));
    }

    @Operation(summary = "详情查询权限")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:view')")
    public ApiResult<SysPermission> detail(@PathVariable Long id) {
        return ApiResult.success(rbacService.getPermission(id));
    }

    @Log(module = "权限管理", type = "修改")
    @Operation(summary = "更新权限")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:update')")
    public ApiResult<SysPermission> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return ApiResult.success(rbacService.updatePermission(id, request));
    }

    @Log(module = "权限管理", type = "删除")
    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        rbacService.deletePermission(id);
        return ApiResult.success();
    }

    @Log(module = "权限管理", type = "批量删除")
    @Operation(summary = "批量删除权限")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        rbacService.batchDeletePermissions(request.ids());
        return ApiResult.success();
    }
}

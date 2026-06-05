package com.teamflow.ai.modules.system.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.IdListRequest;
import com.teamflow.ai.modules.system.dto.RoleRequest;
import com.teamflow.ai.modules.system.entity.SysRole;
import com.teamflow.ai.modules.system.service.RbacService;
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

import java.util.List;

@Tag(name = "角色")
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RbacService rbacService;

    public RoleController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public ApiResult<SysRole> create(@Valid @RequestBody RoleRequest request) {
        return ApiResult.success(rbacService.createRole(request));
    }

    @Operation(summary = "分页查询角色")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResult<PageResult<SysRole>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(rbacService.pageRoles(page, size, keyword));
    }

    @Operation(summary = "详情查询角色")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResult<SysRole> detail(@PathVariable Long id) {
        return ApiResult.success(rbacService.getRole(id));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public ApiResult<SysRole> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return ApiResult.success(rbacService.updateRole(id, request));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        rbacService.deleteRole(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除角色")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        rbacService.batchDeleteRoles(request.ids());
        return ApiResult.success();
    }

    @Operation(summary = "查询角色权限ID")
    @GetMapping("/{id}/permission-ids")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResult<List<Long>> permissionIds(@PathVariable Long id) {
        return ApiResult.success(rbacService.listRolePermissionIds(id));
    }

    @Operation(summary = "分配角色权限")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:update')")
    public ApiResult<Void> assignPermissions(@PathVariable Long id, @RequestBody IdListRequest request) {
        rbacService.assignRolePermissions(id, request.ids());
        return ApiResult.success();
    }
}

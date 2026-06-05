package com.teamflow.ai.modules.user.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.user.dto.UserCreateRequest;
import com.teamflow.ai.modules.user.dto.UserPasswordResetRequest;
import com.teamflow.ai.modules.user.dto.UserPageItem;
import com.teamflow.ai.modules.user.dto.UserRoleRequest;
import com.teamflow.ai.modules.user.dto.UserUpdateRequest;
import com.teamflow.ai.modules.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户")
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public ApiResult<UserPageItem> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResult.success(userAdminService.createUser(request));
    }

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:view')")
    public ApiResult<PageResult<UserPageItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(userAdminService.pageUsers(page, size, keyword));
    }

    @Operation(summary = "查询可分配用户")
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('task:view','project:view','system:user:view')")
    public ApiResult<List<UserPageItem>> options() {
        return ApiResult.success(userAdminService.listAssignableUsers());
    }

    @Operation(summary = "更新用户资料")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResult<UserPageItem> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return ApiResult.success(userAdminService.updateUser(id, request));
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResult<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetRequest request) {
        userAdminService.resetPassword(id, request);
        return ApiResult.success();
    }

    @Operation(summary = "查询用户角色ID")
    @GetMapping("/{id}/role-ids")
    @PreAuthorize("hasAuthority('system:user:view')")
    public ApiResult<List<Long>> roleIds(@PathVariable Long id) {
        return ApiResult.success(userAdminService.listUserRoleIds(id));
    }

    @Operation(summary = "分配用户角色")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResult<Void> assignRoles(@PathVariable Long id, @RequestBody UserRoleRequest request) {
        userAdminService.assignUserRoles(id, request.roleIds());
        return ApiResult.success();
    }
}

package com.teamflow.ai.modules.system.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.system.dto.IdListRequest;
import com.teamflow.ai.modules.system.dto.MenuRequest;
import com.teamflow.ai.modules.system.entity.SysMenu;
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

import java.util.List;

@Tag(name = "菜单")
@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final RbacService rbacService;

    public MenuController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Log(module = "菜单管理", type = "新增")
    @Operation(summary = "创建菜单")
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    public ApiResult<SysMenu> create(@Valid @RequestBody MenuRequest request) {
        return ApiResult.success(rbacService.createMenu(request));
    }

    @Operation(summary = "分页查询菜单")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:menu:view')")
    public ApiResult<PageResult<SysMenu>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(rbacService.pageMenus(page, size, keyword));
    }

    @Operation(summary = "查询菜单树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:view')")
    public ApiResult<List<SysMenu>> tree() {
        return ApiResult.success(rbacService.listMenuTree());
    }

    @Operation(summary = "详情查询菜单")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:view')")
    public ApiResult<SysMenu> detail(@PathVariable Long id) {
        return ApiResult.success(rbacService.getMenu(id));
    }

    @Log(module = "菜单管理", type = "修改")
    @Operation(summary = "更新菜单")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public ApiResult<SysMenu> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return ApiResult.success(rbacService.updateMenu(id, request));
    }

    @Log(module = "菜单管理", type = "删除")
    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        rbacService.deleteMenu(id);
        return ApiResult.success();
    }

    @Log(module = "菜单管理", type = "批量删除")
    @Operation(summary = "批量删除菜单")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        rbacService.batchDeleteMenus(request.ids());
        return ApiResult.success();
    }
}

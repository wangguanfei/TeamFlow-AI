package com.teamflow.ai.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.system.dto.MenuRequest;
import com.teamflow.ai.modules.system.dto.PermissionRequest;
import com.teamflow.ai.modules.system.dto.RoleRequest;
import com.teamflow.ai.modules.system.entity.SysMenu;
import com.teamflow.ai.modules.system.entity.SysPermission;
import com.teamflow.ai.modules.system.entity.SysRole;
import com.teamflow.ai.modules.system.entity.SysRolePermission;
import com.teamflow.ai.modules.system.mapper.SysMenuMapper;
import com.teamflow.ai.modules.system.mapper.SysPermissionMapper;
import com.teamflow.ai.modules.system.mapper.SysRoleMapper;
import com.teamflow.ai.modules.system.mapper.SysRolePermissionMapper;
import com.teamflow.ai.common.cache.PermissionCacheService;
import com.teamflow.ai.common.cache.JsonCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class RbacService {

    /** 菜单树缓存键：全量菜单是低频变更的全局数据，缓存避免每次进入系统管理页都全表扫描。 */
    private static final String MENU_TREE_KEY = "system:menu:tree";
    private static final Duration MENU_TREE_TTL = Duration.ofMinutes(30);

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysMenuMapper menuMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final PermissionCacheService permissionCacheService;
    private final JsonCacheService jsonCacheService;

    public RbacService(
            SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper,
            SysMenuMapper menuMapper,
            SysRolePermissionMapper rolePermissionMapper,
            PermissionCacheService permissionCacheService,
            JsonCacheService jsonCacheService
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionCacheService = permissionCacheService;
        this.jsonCacheService = jsonCacheService;
    }

    public PageResult<SysRole> pageRoles(long page, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysRole::getRoleCode, keyword)
                        .or()
                        .like(SysRole::getRoleName, keyword))
                .orderByAsc(SysRole::getSortNo)
                .orderByDesc(SysRole::getId);
        Page<SysRole> result = roleMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Transactional
    public SysRole createRole(RoleRequest request) {
        if (roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.roleCode())
                .last("LIMIT 1")) != null) {
            throw new BusinessException("角色编码已存在");
        }
        SysRole role = new SysRole();
        fillRole(role, request);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setDeleted(0);
        roleMapper.insert(role);
        return role;
    }

    public SysRole getRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    @Transactional
    public SysRole updateRole(Long id, RoleRequest request) {
        SysRole role = getRole(id);
        if (!role.getRoleCode().equals(request.roleCode()) && roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.roleCode())
                .last("LIMIT 1")) != null) {
            throw new BusinessException("角色编码已存在");
        }
        fillRole(role, request);
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        permissionCacheService.evictAll();
        return role;
    }

    @Transactional
    public void deleteRole(Long id) {
        roleMapper.deleteById(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        permissionCacheService.evictAll();
    }

    @Transactional
    public void batchDeleteRoles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        roleMapper.deleteBatchIds(ids);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, ids));
        permissionCacheService.evictAll();
    }

    public List<Long> listRolePermissionIds(Long roleId) {
        getRole(roleId);
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .toList();
    }

    @Transactional
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        getRole(roleId);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds.stream().distinct().toList()) {
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(roleId);
                relation.setPermissionId(permissionId);
                relation.setCreatedAt(LocalDateTime.now());
                rolePermissionMapper.insert(relation);
            }
        }
        permissionCacheService.evictAll();
    }

    public PageResult<SysPermission> pagePermissions(long page, long size, String keyword) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysPermission::getPermissionCode, keyword)
                        .or()
                        .like(SysPermission::getPermissionName, keyword)
                        .or()
                        .like(SysPermission::getResourcePath, keyword))
                .orderByDesc(SysPermission::getId);
        Page<SysPermission> result = permissionMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Transactional
    public SysPermission createPermission(PermissionRequest request) {
        if (permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, request.permissionCode())
                .last("LIMIT 1")) != null) {
            throw new BusinessException("权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        fillPermission(permission, request);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permission.setDeleted(0);
        permissionMapper.insert(permission);
        permissionCacheService.evictAll();
        return permission;
    }

    public SysPermission getPermission(Long id) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null || permission.getDeleted() == 1) {
            throw new BusinessException("权限不存在");
        }
        return permission;
    }

    @Transactional
    public SysPermission updatePermission(Long id, PermissionRequest request) {
        SysPermission permission = getPermission(id);
        if (!permission.getPermissionCode().equals(request.permissionCode())
                && permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, request.permissionCode())
                .last("LIMIT 1")) != null) {
            throw new BusinessException("权限编码已存在");
        }
        fillPermission(permission, request);
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
        permissionCacheService.evictAll();
        return permission;
    }

    @Transactional
    public void deletePermission(Long id) {
        permissionMapper.deleteById(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getPermissionId, id));
        permissionCacheService.evictAll();
    }

    @Transactional
    public void batchDeletePermissions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        permissionMapper.deleteBatchIds(ids);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getPermissionId, ids));
        permissionCacheService.evictAll();
    }

    public PageResult<SysMenu> pageMenus(long page, long size, String keyword) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysMenu::getMenuName, keyword)
                        .or()
                        .like(SysMenu::getPath, keyword)
                        .or()
                        .like(SysMenu::getPermissionCode, keyword))
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSortNo);
        Page<SysMenu> result = menuMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    public List<SysMenu> listMenuTree() {
        return jsonCacheService.getOrLoad(MENU_TREE_KEY, MENU_TREE_TTL, new TypeReference<List<SysMenu>>() {},
                () -> menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                                .eq(SysMenu::getDeleted, 0)
                                .orderByAsc(SysMenu::getParentId)
                                .orderByAsc(SysMenu::getSortNo))
                        .stream()
                        .sorted(Comparator.comparing(menu -> menu.getSortNo() == null ? 0 : menu.getSortNo()))
                        .toList());
    }

    @Transactional
    public SysMenu createMenu(MenuRequest request) {
        SysMenu menu = new SysMenu();
        fillMenu(menu, request);
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        menu.setDeleted(0);
        menuMapper.insert(menu);
        jsonCacheService.evict(MENU_TREE_KEY);
        return menu;
    }

    public SysMenu getMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null || menu.getDeleted() == 1) {
            throw new BusinessException("菜单不存在");
        }
        return menu;
    }

    @Transactional
    public SysMenu updateMenu(Long id, MenuRequest request) {
        SysMenu menu = getMenu(id);
        fillMenu(menu, request);
        menu.setUpdatedAt(LocalDateTime.now());
        menuMapper.updateById(menu);
        jsonCacheService.evict(MENU_TREE_KEY);
        return menu;
    }

    @Transactional
    public void deleteMenu(Long id) {
        menuMapper.deleteById(id);
        jsonCacheService.evict(MENU_TREE_KEY);
    }

    @Transactional
    public void batchDeleteMenus(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        menuMapper.deleteBatchIds(ids);
        jsonCacheService.evict(MENU_TREE_KEY);
    }

    private void fillRole(SysRole role, RoleRequest request) {
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setScopeType(request.scopeType() == null || request.scopeType().isBlank() ? "SYSTEM" : request.scopeType());
        role.setSortNo(request.sortNo() == null ? 100 : request.sortNo());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setRemark(request.remark());
    }

    private void fillPermission(SysPermission permission, PermissionRequest request) {
        permission.setPermissionCode(request.permissionCode());
        permission.setPermissionName(request.permissionName());
        permission.setResourceType(request.resourceType() == null || request.resourceType().isBlank() ? "API" : request.resourceType());
        permission.setResourcePath(request.resourcePath());
    }

    private void fillMenu(SysMenu menu, MenuRequest request) {
        menu.setParentId(request.parentId() == null ? 0L : request.parentId());
        menu.setMenuName(request.menuName());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setIcon(request.icon());
        menu.setPermissionCode(request.permissionCode());
        menu.setMenuType(request.menuType() == null || request.menuType().isBlank() ? "MENU" : request.menuType());
        menu.setSortNo(request.sortNo() == null ? 100 : request.sortNo());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
    }
}

package com.teamflow.ai.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.modules.system.entity.SysMenu;
import com.teamflow.ai.modules.system.entity.SysPermission;
import com.teamflow.ai.modules.system.entity.SysRole;
import com.teamflow.ai.modules.system.entity.SysRolePermission;
import com.teamflow.ai.modules.system.entity.SysUserRole;
import com.teamflow.ai.modules.system.mapper.SysMenuMapper;
import com.teamflow.ai.modules.system.mapper.SysPermissionMapper;
import com.teamflow.ai.modules.system.mapper.SysRoleMapper;
import com.teamflow.ai.modules.system.mapper.SysRolePermissionMapper;
import com.teamflow.ai.modules.system.mapper.SysUserRoleMapper;
import com.teamflow.ai.common.cache.PermissionCacheService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionQueryService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final PermissionCacheService permissionCacheService;

    public PermissionQueryService(
            SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper,
            SysMenuMapper menuMapper,
            SysUserRoleMapper userRoleMapper,
            SysRolePermissionMapper rolePermissionMapper,
            PermissionCacheService permissionCacheService
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionCacheService = permissionCacheService;
    }

    public List<String> listRoleCodes(Long userId) {
        return permissionCacheService.getRoleCodes(userId, () -> loadRoleCodes(userId));
    }

    public List<String> listPermissionCodes(Long userId) {
        // SUPER_ADMIN 不走缓存：系统新增权限后无需重新登录即可生效
        if (listRoleCodes(userId).contains(SUPER_ADMIN)) {
            return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                            .eq(SysPermission::getDeleted, 0))
                    .stream()
                    .map(SysPermission::getPermissionCode)
                    .toList();
        }
        return permissionCacheService.getPermissionCodes(userId, () -> loadPermissionCodes(userId));
    }

    private List<String> loadRoleCodes(Long userId) {
        List<Long> roleIds = listRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getDeleted() == null || role.getDeleted() == 0)
                .filter(role -> role.getStatus() == null || role.getStatus() == 1)
                .map(SysRole::getRoleCode)
                .toList();
    }

    private List<String> loadPermissionCodes(Long userId) {
        List<Long> roleIds = listRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> permission.getDeleted() == null || permission.getDeleted() == 0)
                .map(SysPermission::getPermissionCode)
                .toList();
    }

    public List<SysMenu> listMenus(Long userId) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
                .eq(SysMenu::getVisible, 1)
                .orderByAsc(SysMenu::getSortNo);
        if (listRoleCodes(userId).contains(SUPER_ADMIN)) {
            return menuMapper.selectList(wrapper);
        }
        Set<String> permissionCodes = Set.copyOf(listPermissionCodes(userId));
        return menuMapper.selectList(wrapper).stream()
                .filter(menu -> menu.getPermissionCode() == null || permissionCodes.contains(menu.getPermissionCode()))
                .sorted(Comparator.comparing(menu -> menu.getSortNo() == null ? 0 : menu.getSortNo()))
                .toList();
    }

    private List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
    }
}

package com.teamflow.ai.modules.team.bootstrap;

import com.teamflow.ai.common.cache.PermissionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 幂等迁移：补齐团队管理菜单、权限码和内置角色绑定。
 */
@Component
@Order(4)
public class TeamSchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TeamSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;
    private final PermissionCacheService permissionCacheService;

    public TeamSchemaUpgrade(JdbcTemplate jdbcTemplate, PermissionCacheService permissionCacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public void run(String... args) {
        int changed = 0;
        changed += ensurePermissions();
        changed += ensureMenu();
        changed += ensureRolePermissions("SUPER_ADMIN", List.of(
                "team:view",
                "team:create",
                "team:update",
                "team:delete",
                "team:member"
        ));
        changed += ensureRolePermissions("DEVELOPER", List.of("team:view"));
        changed += ensureRolePermissions("DEMO_VIEWER", List.of("team:view"));
        if (changed > 0) {
            permissionCacheService.evictAll();
            log.info("团队管理 RBAC 数据补齐完成，changed={}", changed);
        }
    }

    private int ensurePermissions() {
        int changed = 0;
        changed += ensurePermission("team:view", "团队查看", "API", "/api/teams/**");
        changed += ensurePermission("team:create", "团队创建", "API", "/api/teams");
        changed += ensurePermission("team:update", "团队更新", "API", "/api/teams/**");
        changed += ensurePermission("team:delete", "团队删除", "API", "/api/teams/**");
        changed += ensurePermission("team:member", "团队成员管理", "API", "/api/teams/*/members/**");
        return changed;
    }

    private int ensurePermission(String code, String name, String type, String path) {
        return jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, permission_name, resource_type, resource_path, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ? AND deleted = 0)
                """, code, name, type, path, code);
    }

    private int ensureMenu() {
        Long systemMenuId = queryLong("SELECT id FROM sys_menu WHERE path = '/system' AND deleted = 0 LIMIT 1");
        if (systemMenuId == null) {
            return 0;
        }
        return jdbcTemplate.update("""
                INSERT INTO sys_menu(parent_id, menu_name, path, component, icon, permission_code, menu_type, sort_no, visible, created_at, updated_at, deleted)
                SELECT ?, '团队管理', '/system/team', 'TeamManagementView', 'Avatar', 'team:view', 'MENU', 5, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/team' AND deleted = 0)
                """, systemMenuId);
    }

    private int ensureRolePermissions(String roleCode, List<String> permissionCodes) {
        Long roleId = queryLong("SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0 LIMIT 1", roleCode);
        if (roleId == null) {
            return 0;
        }
        int changed = 0;
        for (String permissionCode : permissionCodes) {
            Long permissionId = queryLong(
                    "SELECT id FROM sys_permission WHERE permission_code = ? AND deleted = 0 LIMIT 1",
                    permissionCode);
            if (permissionId != null) {
                changed += ensureRolePermission(roleId, permissionId);
            }
        }
        return changed;
    }

    private int ensureRolePermission(Long roleId, Long permissionId) {
        return jdbcTemplate.update("""
                INSERT INTO sys_role_permission(role_id, permission_id, created_at)
                SELECT ?, ?, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = ? AND permission_id = ?)
                """, roleId, permissionId, roleId, permissionId);
    }

    private Long queryLong(String sql, Object... args) {
        var list = jdbcTemplate.query(sql, (rs, i) -> rs.getLong(1), args);
        return list.isEmpty() ? null : list.get(0);
    }
}

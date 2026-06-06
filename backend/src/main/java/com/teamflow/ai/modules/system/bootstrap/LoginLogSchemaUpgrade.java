package com.teamflow.ai.modules.system.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等迁移：为存量数据库的 login_log 表补充富化字段。
 * 每次启动都运行，通过 information_schema 判断列是否存在。
 */
@Component
@Order(1)
public class LoginLogSchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoginLogSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    public LoginLogSchemaUpgrade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfAbsent("login_location", "VARCHAR(128) DEFAULT NULL COMMENT '登录地点'", "user_agent");
        addColumnIfAbsent("browser", "VARCHAR(64) DEFAULT NULL COMMENT '浏览器'", "login_location");
        addColumnIfAbsent("os", "VARCHAR(64) DEFAULT NULL COMMENT '操作系统'", "browser");
        ensureIndex("idx_status", "ALTER TABLE login_log ADD INDEX idx_status (status)");
        ensureIndex("idx_created_at", "ALTER TABLE login_log ADD INDEX idx_created_at (created_at)");
        ensureLoginLogPermissions();
        ensureLoginLogMenu();
    }

    private void addColumnIfAbsent(String columnName, String columnDef, String afterColumn) {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'login_log' AND COLUMN_NAME = ?",
                (rs, i) -> 1, columnName).isEmpty();
        if (!exists) {
            jdbcTemplate.execute(
                    "ALTER TABLE login_log ADD COLUMN `" + columnName + "` " + columnDef + " AFTER `" + afterColumn + "`");
            log.info("login_log 表新增列: {}", columnName);
        }
    }

    private void ensureIndex(String indexName, String ddl) {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'login_log' AND INDEX_NAME = ?",
                (rs, i) -> 1, indexName).isEmpty();
        if (!exists) {
            try {
                jdbcTemplate.execute(ddl);
                log.info("login_log 表新增索引: {}", indexName);
            } catch (Exception e) {
                log.warn("创建索引 {} 失败（可能已存在）: {}", indexName, e.getMessage());
            }
        }
    }

    private void ensureLoginLogPermissions() {
        ensurePermission("system:loginlog:view", "登录日志查看", "API", "/api/login-logs/**");
        ensurePermission("system:loginlog:delete", "登录日志删除", "API", "/api/login-logs/**");
    }

    private void ensurePermission(String code, String name, String type, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, permission_name, resource_type, resource_path, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ? AND deleted = 0)
                """, code, name, type, path, code);
    }

    private void ensureLoginLogMenu() {
        Long systemMenuId = queryLong(
                "SELECT id FROM sys_menu WHERE path = '/system' AND deleted = 0 LIMIT 1");
        if (systemMenuId == null) return;
        jdbcTemplate.update("""
                INSERT INTO sys_menu(parent_id, menu_name, path, component, icon, permission_code, menu_type, sort_no, visible, created_at, updated_at, deleted)
                SELECT ?, '登录日志', '/system/login-log', 'LoginLogView', 'Document', 'system:loginlog:view', 'MENU', 50, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/login-log' AND deleted = 0)
                """, systemMenuId);

        // 为 admin 的所有角色授予新权限
        Long viewId = queryLong("SELECT id FROM sys_permission WHERE permission_code = 'system:loginlog:view' AND deleted = 0 LIMIT 1");
        Long deleteId = queryLong("SELECT id FROM sys_permission WHERE permission_code = 'system:loginlog:delete' AND deleted = 0 LIMIT 1");
        Long adminRoleId = queryLong("SELECT id FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND deleted = 0 LIMIT 1");
        Long demoRoleId = queryLong("SELECT id FROM sys_role WHERE role_code = 'DEMO_VIEWER' AND deleted = 0 LIMIT 1");

        if (adminRoleId != null && viewId != null) ensureRolePermission(adminRoleId, viewId);
        if (adminRoleId != null && deleteId != null) ensureRolePermission(adminRoleId, deleteId);
        if (demoRoleId != null && viewId != null) ensureRolePermission(demoRoleId, viewId);
    }

    private void ensureRolePermission(Long roleId, Long permissionId) {
        jdbcTemplate.update("""
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

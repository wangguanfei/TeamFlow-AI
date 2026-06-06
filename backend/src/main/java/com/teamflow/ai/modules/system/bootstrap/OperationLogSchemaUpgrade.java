package com.teamflow.ai.modules.system.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等迁移：补充 operation_log 表字段、权限码及菜单。
 * 每次启动都运行，通过 information_schema / SELECT 1 判断是否已存在。
 */
@Component
@Order(2)
public class OperationLogSchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OperationLogSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    public OperationLogSchemaUpgrade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfAbsent("operation_log", "username", "VARCHAR(64) DEFAULT NULL COMMENT '操作人账号'", "user_id");
        addColumnIfAbsent("operation_log", "error_message", "VARCHAR(500) DEFAULT NULL COMMENT '异常信息'", "response_status");
        ensureIndex("operation_log", "idx_username", "ALTER TABLE operation_log ADD INDEX idx_username (username)");
        ensureIndex("operation_log", "idx_module", "ALTER TABLE operation_log ADD INDEX idx_module (module_name)");
        ensureIndex("operation_log", "idx_created_at", "ALTER TABLE operation_log ADD INDEX idx_created_at (created_at)");
        ensurePermissions();
        ensureMenu();
    }

    private void addColumnIfAbsent(String table, String columnName, String columnDef, String afterColumn) {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                (rs, i) -> 1, table, columnName).isEmpty();
        if (!exists) {
            jdbcTemplate.execute(
                    "ALTER TABLE `" + table + "` ADD COLUMN `" + columnName + "` " + columnDef + " AFTER `" + afterColumn + "`");
            log.info("{} 表新增列: {}", table, columnName);
        }
    }

    private void ensureIndex(String table, String indexName, String ddl) {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                (rs, i) -> 1, table, indexName).isEmpty();
        if (!exists) {
            try {
                jdbcTemplate.execute(ddl);
                log.info("{} 表新增索引: {}", table, indexName);
            } catch (Exception e) {
                log.warn("创建索引 {} 失败（可能已存在）: {}", indexName, e.getMessage());
            }
        }
    }

    private void ensurePermissions() {
        ensurePermission("system:operlog:view", "操作日志查看", "API", "/api/operation-logs/**");
        ensurePermission("system:operlog:delete", "操作日志删除", "API", "/api/operation-logs/**");
    }

    private void ensurePermission(String code, String name, String type, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, permission_name, resource_type, resource_path, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ? AND deleted = 0)
                """, code, name, type, path, code);
    }

    private void ensureMenu() {
        Long systemMenuId = queryLong(
                "SELECT id FROM sys_menu WHERE path = '/system' AND deleted = 0 LIMIT 1");
        if (systemMenuId == null) return;

        jdbcTemplate.update("""
                INSERT INTO sys_menu(parent_id, menu_name, path, component, icon, permission_code, menu_type, sort_no, visible, created_at, updated_at, deleted)
                SELECT ?, '操作日志', '/system/operation-log', 'OperationLogView', 'Tickets', 'system:operlog:view', 'MENU', 60, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/operation-log' AND deleted = 0)
                """, systemMenuId);

        Long viewId = queryLong("SELECT id FROM sys_permission WHERE permission_code = 'system:operlog:view' AND deleted = 0 LIMIT 1");
        Long deleteId = queryLong("SELECT id FROM sys_permission WHERE permission_code = 'system:operlog:delete' AND deleted = 0 LIMIT 1");
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

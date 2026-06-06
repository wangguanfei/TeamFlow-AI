package com.teamflow.ai.modules.deploy.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等迁移：建表 deploy_record、插入权限码和菜单，仅绑定 SUPER_ADMIN 角色。
 */
@Component
@Order(3)
public class DeploySchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DeploySchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    public DeploySchemaUpgrade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureTable();
        ensurePermissions();
        ensureMenu();
    }

    private void ensureTable() {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'deploy_record'",
                (rs, i) -> 1).isEmpty();
        if (!exists) {
            jdbcTemplate.execute("""
                    CREATE TABLE deploy_record (
                      id               BIGINT       NOT NULL AUTO_INCREMENT,
                      target           VARCHAR(16)  NOT NULL COMMENT '部署目标 all/backend/frontend',
                      status           VARCHAR(16)  NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
                      trigger_user_id  BIGINT       DEFAULT NULL,
                      trigger_username VARCHAR(64)  DEFAULT NULL,
                      exit_code        INT          DEFAULT NULL,
                      log_file         VARCHAR(512) DEFAULT NULL,
                      started_at       DATETIME     NOT NULL,
                      finished_at      DATETIME     DEFAULT NULL,
                      cost_ms          BIGINT       DEFAULT NULL,
                      created_at       DATETIME     NOT NULL,
                      PRIMARY KEY (id),
                      KEY idx_status (status),
                      KEY idx_started_at (started_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署记录'
                    """);
            log.info("创建表 deploy_record 完成");
        }
    }

    private void ensurePermissions() {
        ensurePermission("system:deploy:view", "部署历史查看", "API", "/api/deploy/**");
        ensurePermission("system:deploy:exec", "部署执行", "API", "/api/deploy/**");
    }

    private void ensurePermission(String code, String name, String type, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, permission_name, resource_type, resource_path, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ? AND deleted = 0)
                """, code, name, type, path, code);
    }

    private void ensureMenu() {
        Long systemMenuId = queryLong("SELECT id FROM sys_menu WHERE path = '/system' AND deleted = 0 LIMIT 1");
        if (systemMenuId == null) return;

        jdbcTemplate.update("""
                INSERT INTO sys_menu(parent_id, menu_name, path, component, icon, permission_code, menu_type, sort_no, visible, created_at, updated_at, deleted)
                SELECT ?, '部署管理', '/system/deploy', 'DeployManagementView', 'Setting', 'system:deploy:view', 'MENU', 70, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/deploy' AND deleted = 0)
                """, systemMenuId);

        Long viewId = queryLong("SELECT id FROM sys_permission WHERE permission_code = 'system:deploy:view' AND deleted = 0 LIMIT 1");
        Long execId = queryLong("SELECT id FROM sys_permission WHERE permission_code = 'system:deploy:exec' AND deleted = 0 LIMIT 1");
        Long adminRoleId = queryLong("SELECT id FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND deleted = 0 LIMIT 1");

        if (adminRoleId != null && viewId != null) ensureRolePermission(adminRoleId, viewId);
        if (adminRoleId != null && execId != null) ensureRolePermission(adminRoleId, execId);
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

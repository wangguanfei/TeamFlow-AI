package com.teamflow.ai.modules.agent.bootstrap;

import com.teamflow.ai.common.cache.PermissionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(6)
public class AgentSchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;
    private final PermissionCacheService permissionCacheService;

    public AgentSchemaUpgrade(JdbcTemplate jdbcTemplate, PermissionCacheService permissionCacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public void run(String... args) {
        int changed = 0;
        // 项目当前使用启动期补丁兼容线上旧库；这里只补 Agent 相关表、权限和菜单。
        ensureActionTable();
        changed += ensurePermission("ai:agent", "AI企业助理", "API", "/api/ai/agent/**");
        changed += ensurePermission("ai:agent:action:view", "AI企业助理审计查看", "API", "/api/ai/agent/actions/**");
        changed += ensureRolePermissions("SUPER_ADMIN", List.of("ai:agent", "ai:agent:action:view"));
        changed += ensureRolePermissions("DEVELOPER", List.of("ai:agent"));
        changed += ensureRolePermissions("DEMO_VIEWER", List.of("ai:agent"));
        changed += ensureAgentActionMenu();
        if (changed > 0) {
            permissionCacheService.evictAll();
            log.info("AI企业助理 RBAC 数据补齐完成，changed={}", changed);
        }
    }

    private void ensureActionTable() {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_agent_action'",
                (rs, i) -> 1).isEmpty();
        if (exists) {
            return;
        }
        // ai_agent_action 是 Agent 安全审计核心表：写操作预览、确认 token hash、执行结果都在这里追溯。
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_action (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
                  session_id BIGINT DEFAULT NULL COMMENT 'AI会话ID',
                  message_id BIGINT DEFAULT NULL COMMENT '触发消息ID',
                  user_id BIGINT NOT NULL COMMENT '发起用户ID',
                  tool_name VARCHAR(128) NOT NULL COMMENT '工具名',
                  tool_label VARCHAR(128) DEFAULT NULL COMMENT '工具中文名',
                  arguments_json JSON DEFAULT NULL COMMENT '工具参数',
                  preview_json JSON DEFAULT NULL COMMENT '写操作预览',
                  result_json JSON DEFAULT NULL COMMENT '执行结果',
                  is_write TINYINT NOT NULL DEFAULT 0 COMMENT '是否写操作',
                  status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/EXECUTED/CANCELLED/FAILED',
                  confirm_token_hash VARCHAR(128) DEFAULT NULL COMMENT '确认token摘要',
                  confirmed_by BIGINT DEFAULT NULL COMMENT '确认人',
                  confirmed_at DATETIME DEFAULT NULL COMMENT '确认时间',
                  target_type VARCHAR(64) DEFAULT NULL COMMENT '业务对象类型',
                  target_id BIGINT DEFAULT NULL COMMENT '业务对象ID',
                  error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
                  duration_ms BIGINT DEFAULT NULL COMMENT '执行耗时',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_session_id (session_id),
                  KEY idx_user_id (user_id),
                  KEY idx_tool_name (tool_name),
                  KEY idx_status (status),
                  KEY idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Agent工具调用日志'
                """);
        log.info("创建表 ai_agent_action 完成");
    }

    private int ensurePermission(String code, String name, String type, String path) {
        return jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, permission_name, resource_type, resource_path, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ? AND deleted = 0)
                """, code, name, type, path, code);
    }

    private int ensureRolePermissions(String roleCode, List<String> permissionCodes) {
        Long roleId = queryLong("SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0 LIMIT 1", roleCode);
        if (roleId == null) {
            return 0;
        }
        int changed = 0;
        for (String permissionCode : permissionCodes) {
            Long permissionId = queryLong("SELECT id FROM sys_permission WHERE permission_code = ? AND deleted = 0 LIMIT 1", permissionCode);
            if (permissionId != null) {
                changed += jdbcTemplate.update("""
                        INSERT INTO sys_role_permission(role_id, permission_id, created_at)
                        SELECT ?, ?, NOW()
                        WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = ? AND permission_id = ?)
                        """, roleId, permissionId, roleId, permissionId);
            }
        }
        return changed;
    }

    private int ensureAgentActionMenu() {
        Long systemMenuId = queryLong("SELECT id FROM sys_menu WHERE path = '/system' AND deleted = 0 LIMIT 1");
        if (systemMenuId == null) {
            return 0;
        }
        return jdbcTemplate.update("""
                INSERT INTO sys_menu(parent_id, menu_name, path, component, icon, permission_code, menu_type, sort_no, visible, created_at, updated_at, deleted)
                SELECT ?, 'AI助理审计', '/system/agent-actions', 'AgentActionLogView', 'Tickets', 'ai:agent:action:view', 'MENU', 65, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/agent-actions' AND deleted = 0)
                """, systemMenuId);
    }

    private Long queryLong(String sql, Object... args) {
        var list = jdbcTemplate.query(sql, (rs, i) -> rs.getLong(1), args);
        return list.isEmpty() ? null : list.get(0);
    }
}

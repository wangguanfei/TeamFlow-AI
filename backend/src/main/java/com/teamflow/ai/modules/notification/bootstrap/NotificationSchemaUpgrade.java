package com.teamflow.ai.modules.notification.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等迁移：为通知补充业务关联和业务时间，避免把消息接收时间误当业务时间。
 */
@Component
@Order(5)
public class NotificationSchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaUpgrade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfAbsent("biz_type", "VARCHAR(32) DEFAULT NULL COMMENT '业务类型'", "sender_id");
        addColumnIfAbsent("biz_id", "BIGINT DEFAULT NULL COMMENT '业务ID'", "biz_type");
        addColumnIfAbsent("biz_time", "DATETIME DEFAULT NULL COMMENT '业务时间'", "biz_id");
        ensureIndex("idx_biz", "ALTER TABLE notification ADD INDEX idx_biz (biz_type, biz_id)");
        ensureIndex("idx_biz_time", "ALTER TABLE notification ADD INDEX idx_biz_time (biz_time)");
    }

    private void addColumnIfAbsent(String columnName, String columnDef, String afterColumn) {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = ?",
                (rs, i) -> 1, columnName).isEmpty();
        if (!exists) {
            jdbcTemplate.execute(
                    "ALTER TABLE notification ADD COLUMN `" + columnName + "` " + columnDef + " AFTER `" + afterColumn + "`");
            log.info("notification 表新增列: {}", columnName);
        }
    }

    private void ensureIndex(String indexName, String ddl) {
        boolean exists = !jdbcTemplate.query(
                "SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = ?",
                (rs, i) -> 1, indexName).isEmpty();
        if (!exists) {
            try {
                jdbcTemplate.execute(ddl);
                log.info("notification 表新增索引: {}", indexName);
            } catch (Exception e) {
                log.warn("创建 notification 索引 {} 失败（可能已存在）: {}", indexName, e.getMessage());
            }
        }
    }
}

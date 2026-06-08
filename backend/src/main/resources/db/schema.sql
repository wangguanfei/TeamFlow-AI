
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 用户ID',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '登录账号,唯一',
  `password` VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt密码',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT '头像',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录',
  `last_login_ip` VARCHAR(64) DEFAULT NULL COMMENT '最后IP',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_user';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `role_code` VARCHAR(64) DEFAULT NULL COMMENT '角色编码,唯一',
  `role_name` VARCHAR(64) DEFAULT NULL COMMENT '角色名称',
  `scope_type` VARCHAR(32) DEFAULT NULL COMMENT 'SYSTEM/TEAM/PROJECT',
  `sort_no` INT DEFAULT NULL COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_role';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `permission_code` VARCHAR(128) DEFAULT NULL COMMENT '权限编码,唯一',
  `permission_name` VARCHAR(128) DEFAULT NULL COMMENT '权限名称',
  `resource_type` VARCHAR(32) DEFAULT NULL COMMENT 'MENU/BUTTON/API/DATA',
  `resource_path` VARCHAR(255) DEFAULT NULL COMMENT '资源路径',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_permission';

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父菜单',
  `menu_name` VARCHAR(64) DEFAULT NULL COMMENT '菜单名称',
  `path` VARCHAR(255) DEFAULT NULL COMMENT '路由',
  `component` VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
  `icon` VARCHAR(64) DEFAULT NULL COMMENT '图标',
  `permission_code` VARCHAR(128) DEFAULT NULL COMMENT '权限码',
  `menu_type` VARCHAR(16) DEFAULT NULL COMMENT 'DIR/MENU/BUTTON',
  `sort_no` INT DEFAULT NULL COMMENT '排序',
  `visible` TINYINT DEFAULT NULL COMMENT '是否显示',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_menu';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `role_id` BIGINT DEFAULT NULL COMMENT '角色ID',
  `scope_type` VARCHAR(32) DEFAULT NULL COMMENT 'SYSTEM/TEAM/PROJECT',
  `scope_id` BIGINT DEFAULT NULL COMMENT '作用域ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_scope_id` (`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_user_role';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `role_id` BIGINT DEFAULT NULL COMMENT '角色ID',
  `permission_id` BIGINT DEFAULT NULL COMMENT '权限ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_role_permission';

CREATE TABLE IF NOT EXISTS `sys_bootstrap_marker` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `marker_key` VARCHAR(128) NOT NULL COMMENT '初始化标记',
  `marker_value` VARCHAR(255) DEFAULT NULL COMMENT '标记值',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marker_key` (`marker_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='sys_bootstrap_marker';

CREATE TABLE IF NOT EXISTS `team` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `team_name` VARCHAR(128) DEFAULT NULL COMMENT '团队名称',
  `team_code` VARCHAR(64) DEFAULT NULL COMMENT '团队编码',
  `owner_id` BIGINT DEFAULT NULL COMMENT '负责人',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_team_code` (`team_code`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='team';

CREATE TABLE IF NOT EXISTS `team_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `team_id` BIGINT DEFAULT NULL COMMENT '团队ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `member_role` VARCHAR(32) DEFAULT NULL COMMENT 'OWNER/ADMIN/MEMBER',
  `join_time` DATETIME DEFAULT NULL COMMENT '加入时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='team_member';

CREATE TABLE IF NOT EXISTS `project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `team_id` BIGINT DEFAULT NULL COMMENT '团队ID',
  `project_code` VARCHAR(64) DEFAULT NULL COMMENT '项目编码',
  `project_name` VARCHAR(128) DEFAULT NULL COMMENT '项目名称',
  `description` TEXT DEFAULT NULL COMMENT '描述',
  `owner_id` BIGINT DEFAULT NULL COMMENT '负责人',
  `start_date` DATE DEFAULT NULL COMMENT '开始日期',
  `end_date` DATE DEFAULT NULL COMMENT '结束日期',
  `status` VARCHAR(32) NOT NULL DEFAULT 1 COMMENT 'PLANNING/ACTIVE/DONE/ARCHIVED',
  `progress` DECIMAL(5,2) DEFAULT NULL COMMENT '进度',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_project_code` (`project_code`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='project';

CREATE TABLE IF NOT EXISTS `project_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `project_id` BIGINT DEFAULT NULL COMMENT '项目ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `project_role` VARCHAR(32) DEFAULT NULL COMMENT 'PM/DEV/TEST/VIEWER',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='project_member';

CREATE TABLE IF NOT EXISTS `project_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `project_id` BIGINT DEFAULT NULL COMMENT '项目ID',
  `tag_name` VARCHAR(64) DEFAULT NULL COMMENT '标签名',
  `tag_color` VARCHAR(32) DEFAULT NULL COMMENT '颜色',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='project_tag';

CREATE TABLE IF NOT EXISTS `task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `project_id` BIGINT DEFAULT NULL COMMENT '项目ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父任务',
  `task_no` VARCHAR(64) DEFAULT NULL COMMENT '任务编号',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '标题',
  `description` TEXT DEFAULT NULL COMMENT '描述',
  `assignee_id` BIGINT DEFAULT NULL COMMENT '负责人',
  `reporter_id` BIGINT DEFAULT NULL COMMENT '报告人',
  `priority` VARCHAR(16) DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH/URGENT',
  `status` VARCHAR(32) NOT NULL DEFAULT 1 COMMENT 'TODO/DOING/TESTING/DONE/CLOSED',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `due_time` DATETIME DEFAULT NULL COMMENT '截止时间',
  `estimate_hours` DECIMAL(8,2) DEFAULT NULL COMMENT '预估工时',
  `actual_hours` DECIMAL(8,2) DEFAULT NULL COMMENT '实际工时',
  `sort_no` INT DEFAULT NULL COMMENT '看板排序',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_task_no` (`task_no`),
  KEY `idx_assignee_id` (`assignee_id`),
  KEY `idx_reporter_id` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task';

CREATE TABLE IF NOT EXISTS `task_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '评论人',
  `content` TEXT DEFAULT NULL COMMENT '内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task_comment';

CREATE TABLE IF NOT EXISTS `task_executor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '执行人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_user` (`task_id`,`user_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task_executor';

CREATE TABLE IF NOT EXISTS `task_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `file_id` BIGINT DEFAULT NULL COMMENT '文件ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '上传人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task_attachment';

CREATE TABLE IF NOT EXISTS `task_worklog` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户',
  `work_date` DATE DEFAULT NULL COMMENT '工作日期',
  `hours` DECIMAL(8,2) DEFAULT NULL COMMENT '工时',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task_worklog';

CREATE TABLE IF NOT EXISTS `task_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `tag_name` VARCHAR(64) DEFAULT NULL COMMENT '标签名',
  `tag_color` VARCHAR(32) DEFAULT NULL COMMENT '颜色',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task_tag';

CREATE TABLE IF NOT EXISTS `knowledge_space` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `team_id` BIGINT DEFAULT NULL COMMENT '团队ID',
  `space_name` VARCHAR(128) DEFAULT NULL COMMENT '空间名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `visibility` VARCHAR(32) DEFAULT NULL COMMENT 'PRIVATE/TEAM/PUBLIC',
  `owner_id` BIGINT DEFAULT NULL COMMENT '负责人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge_space';

CREATE TABLE IF NOT EXISTS `knowledge_doc` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `space_id` BIGINT DEFAULT NULL COMMENT '空间ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父文档',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '标题',
  `content_md` LONGTEXT DEFAULT NULL COMMENT 'Markdown内容',
  `content_text` LONGTEXT DEFAULT NULL COMMENT '纯文本',
  `author_id` BIGINT DEFAULT NULL COMMENT '作者',
  `doc_status` VARCHAR(32) DEFAULT NULL COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  `sort_no` INT DEFAULT NULL COMMENT '排序',
  `version_no` INT DEFAULT NULL COMMENT '当前版本',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_space_id` (`space_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge_doc';

CREATE TABLE IF NOT EXISTS `knowledge_doc_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `doc_id` BIGINT DEFAULT NULL COMMENT '文档ID',
  `version_no` INT DEFAULT NULL COMMENT '版本号',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '标题快照',
  `content_md` LONGTEXT DEFAULT NULL COMMENT '内容快照',
  `editor_id` BIGINT DEFAULT NULL COMMENT '编辑人',
  `change_summary` VARCHAR(500) DEFAULT NULL COMMENT '变更说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_editor_id` (`editor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge_doc_version';

CREATE TABLE IF NOT EXISTS `knowledge_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `doc_id` BIGINT DEFAULT NULL COMMENT '文档ID',
  `tag_name` VARCHAR(64) DEFAULT NULL COMMENT '标签',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge_tag';

CREATE TABLE IF NOT EXISTS `knowledge_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `doc_id` BIGINT DEFAULT NULL COMMENT '文档ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge_favorite';

CREATE TABLE IF NOT EXISTS `ai_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `space_id` BIGINT DEFAULT NULL COMMENT '知识空间ID',
  `session_title` VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
  `model_name` VARCHAR(64) DEFAULT NULL COMMENT '模型',
  `session_type` VARCHAR(32) DEFAULT NULL COMMENT 'CHAT/KNOWLEDGE/SUMMARY/CODE/SQL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ai_session';

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `session_id` BIGINT DEFAULT NULL COMMENT '会话ID',
  `role` VARCHAR(16) DEFAULT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  `content` LONGTEXT DEFAULT NULL COMMENT '消息内容',
  `tokens` INT DEFAULT NULL COMMENT 'Token数量',
  `references_json` JSON DEFAULT NULL COMMENT '引用来源',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ai_message';

CREATE TABLE IF NOT EXISTS `ai_embedding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `doc_id` BIGINT DEFAULT NULL COMMENT '文档ID',
  `chunk_index` INT DEFAULT NULL COMMENT '切片序号',
  `chunk_text` TEXT DEFAULT NULL COMMENT '切片文本',
  `embedding_hash` VARCHAR(128) DEFAULT NULL COMMENT '向量哈希',
  `embedding_text` MEDIUMTEXT DEFAULT NULL COMMENT '兼容字段,旧版演示向量字符串',
  `embedding_model` VARCHAR(128) DEFAULT NULL COMMENT 'Embedding模型',
  `embedding_dim` INT DEFAULT NULL COMMENT '向量维度',
  `vector_point_id` VARCHAR(64) DEFAULT NULL COMMENT 'Qdrant point id',
  `content_hash` VARCHAR(128) DEFAULT NULL COMMENT '切片内容哈希',
  `index_status` VARCHAR(32) DEFAULT NULL COMMENT 'PENDING/READY/FAILED/MANUAL',
  `indexed_at` DATETIME DEFAULT NULL COMMENT '索引完成时间',
  `index_error` VARCHAR(1000) DEFAULT NULL COMMENT '索引错误',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_vector_point_id` (`vector_point_id`),
  KEY `idx_index_status` (`index_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ai_embedding';

-- 兼容旧库：为已存在的 ai_embedding 旧表补齐 RAG 新列。
-- 注意：MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`（那是 MariaDB/PostgreSQL 语法）。
-- 这里去掉 IF NOT EXISTS，靠 spring.sql.init.continue-on-error=true 实现幂等：
-- 旧表缺列时 ADD 成功补列；新库 CREATE TABLE 已含这些列，重复 ADD 报错会被容错跳过。
ALTER TABLE `ai_embedding` ADD COLUMN `embedding_model` VARCHAR(128) DEFAULT NULL COMMENT 'Embedding模型';
ALTER TABLE `ai_embedding` ADD COLUMN `embedding_dim` INT DEFAULT NULL COMMENT '向量维度';
ALTER TABLE `ai_embedding` ADD COLUMN `vector_point_id` VARCHAR(64) DEFAULT NULL COMMENT 'Qdrant point id';
ALTER TABLE `ai_embedding` ADD COLUMN `content_hash` VARCHAR(128) DEFAULT NULL COMMENT '切片内容哈希';
ALTER TABLE `ai_embedding` ADD COLUMN `index_status` VARCHAR(32) DEFAULT NULL COMMENT 'PENDING/READY/FAILED/MANUAL';
ALTER TABLE `ai_embedding` ADD COLUMN `indexed_at` DATETIME DEFAULT NULL COMMENT '索引完成时间';
ALTER TABLE `ai_embedding` ADD COLUMN `index_error` VARCHAR(1000) DEFAULT NULL COMMENT '索引错误';

CREATE TABLE IF NOT EXISTS `ai_index_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `doc_id` BIGINT NOT NULL COMMENT '文档ID',
  `action_type` VARCHAR(32) NOT NULL COMMENT 'REBUILD/DELETE',
  `job_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `locked_at` DATETIME DEFAULT NULL COMMENT '锁定时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_job_status` (`job_status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ai_index_job';

CREATE TABLE IF NOT EXISTS `file_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `biz_type` VARCHAR(64) DEFAULT NULL COMMENT '业务类型',
  `biz_id` BIGINT DEFAULT NULL COMMENT '业务ID',
  `bucket_name` VARCHAR(64) DEFAULT NULL COMMENT 'MinIO桶',
  `object_key` VARCHAR(500) DEFAULT NULL COMMENT '对象Key',
  `original_name` VARCHAR(255) DEFAULT NULL COMMENT '原文件名',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME',
  `file_size` BIGINT DEFAULT NULL COMMENT '大小',
  `file_ext` VARCHAR(32) DEFAULT NULL COMMENT '扩展名',
  `uploader_id` BIGINT DEFAULT NULL COMMENT '上传人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_biz_id` (`biz_id`),
  KEY `idx_uploader_id` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='file_info';

CREATE TABLE IF NOT EXISTS `file_share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `file_id` BIGINT DEFAULT NULL COMMENT '文件ID',
  `share_code` VARCHAR(64) DEFAULT NULL COMMENT '分享码',
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`),
  KEY `idx_share_code` (`share_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='file_share';

CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '标题',
  `content` TEXT DEFAULT NULL COMMENT '内容',
  `notify_type` VARCHAR(32) DEFAULT NULL COMMENT 'SYSTEM/PROJECT/TASK/AI',
  `target_type` VARCHAR(32) DEFAULT NULL COMMENT 'USER/TEAM/PROJECT',
  `target_id` BIGINT DEFAULT NULL COMMENT '目标ID',
  `sender_id` BIGINT DEFAULT NULL COMMENT '发送人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_target_id` (`target_id`),
  KEY `idx_sender_id` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='notification';

CREATE TABLE IF NOT EXISTS `notification_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `notification_id` BIGINT DEFAULT NULL COMMENT '通知ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `read_time` DATETIME DEFAULT NULL COMMENT '已读时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_id` (`notification_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='notification_read';

CREATE TABLE IF NOT EXISTS `login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '账号',
  `login_ip` VARCHAR(64) DEFAULT NULL COMMENT 'IP',
  `login_location` VARCHAR(128) DEFAULT NULL COMMENT '登录地点',
  `browser` VARCHAR(64) DEFAULT NULL COMMENT '浏览器',
  `os` VARCHAR(64) DEFAULT NULL COMMENT '操作系统',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT 'UA',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功',
  `message` VARCHAR(255) DEFAULT NULL COMMENT '消息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='login_log';

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '操作人账号',
  `module_name` VARCHAR(64) DEFAULT NULL COMMENT '模块',
  `operation_type` VARCHAR(64) DEFAULT NULL COMMENT '操作类型',
  `request_method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP方法',
  `request_uri` VARCHAR(255) DEFAULT NULL COMMENT 'URI',
  `request_params` TEXT DEFAULT NULL COMMENT '参数（敏感字段已脱敏）',
  `response_status` INT DEFAULT NULL COMMENT '响应码',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '异常信息',
  `cost_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
  `client_ip` VARCHAR(64) DEFAULT NULL COMMENT 'IP',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_username` (`username`),
  KEY `idx_module` (`module_name`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='operation_log';

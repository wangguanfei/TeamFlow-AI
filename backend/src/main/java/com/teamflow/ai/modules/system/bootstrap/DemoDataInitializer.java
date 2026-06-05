package com.teamflow.ai.modules.system.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.security.DemoAccountConstants;
import com.teamflow.ai.modules.file.service.FileService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "teamflow.demo-data", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String BOOTSTRAP_MARKER_KEY = "demo-data-initialized";
    private static final String BOOTSTRAP_MARKER_VALUE = "v1";

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123456";

    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    public DemoDataInitializer(
            JdbcTemplate jdbcTemplate,
            SysUserMapper userMapper,
            PasswordEncoder passwordEncoder,
            FileService fileService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileService = fileService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (bootstrapMarkerExists()) {
            log.info("演示初始化数据已完成，跳过 DemoDataInitializer");
            return;
        }
        if (hasExistingApplicationData()) {
            markBootstrapCompleted("existing-data");
            log.info("检测到已有业务数据，补写初始化标记并跳过 DemoDataInitializer");
            return;
        }

        Long adminRoleId = ensureRole("SUPER_ADMIN", "超级管理员", 1, "系统内置角色");
        Long developerRoleId = ensureRole("DEVELOPER", "开发工程师", 20, "演示用普通角色");
        Long demoViewerRoleId = ensureRole(DemoAccountConstants.ROLE_CODE, "只读演示账号", 30, "演示账号专用角色，仅允许查看数据");
        Long dashboardPermissionId = ensurePermission("dashboard:view", "工作台查看", "MENU", "/api/dashboard/**");
        Long teamViewPermissionId = ensurePermission("team:view", "团队查看", "API", "/api/teams/**");
        Long teamCreatePermissionId = ensurePermission("team:create", "团队创建", "API", "/api/teams");
        Long projectViewPermissionId = ensurePermission("project:view", "项目查看", "API", "/api/projects/**");
        Long projectCreatePermissionId = ensurePermission("project:create", "项目创建", "API", "/api/projects");
        Long projectUpdatePermissionId = ensurePermission("project:update", "项目更新", "API", "/api/projects/**");
        Long projectDeletePermissionId = ensurePermission("project:delete", "项目删除", "API", "/api/projects/**");
        Long projectMemberPermissionId = ensurePermission("project:member", "项目成员管理", "API", "/api/project-members/**");
        Long projectTagPermissionId = ensurePermission("project:tag", "项目标签管理", "API", "/api/project-tags/**");
        Long taskViewPermissionId = ensurePermission("task:view", "任务查看", "API", "/api/tasks/**");
        Long taskCreatePermissionId = ensurePermission("task:create", "任务创建", "API", "/api/tasks");
        Long taskUpdatePermissionId = ensurePermission("task:update", "任务更新", "API", "/api/tasks/**");
        Long taskDeletePermissionId = ensurePermission("task:delete", "任务删除", "API", "/api/tasks/**");
        Long taskCommentPermissionId = ensurePermission("task:comment", "任务评论", "API", "/api/task-comments/**");
        Long taskWorklogPermissionId = ensurePermission("task:worklog", "任务工时", "API", "/api/task-worklogs/**");
        Long taskAttachmentPermissionId = ensurePermission("task:attachment", "任务附件", "API", "/api/task-attachments/**");
        Long taskTagPermissionId = ensurePermission("task:tag", "任务标签", "API", "/api/task-tags/**");
        Long knowledgeViewPermissionId = ensurePermission("knowledge:view", "知识库查看", "API", "/api/knowledge-*/**");
        Long knowledgeSpaceCreatePermissionId = ensurePermission("knowledge:space:create", "知识空间创建", "API", "/api/knowledge-spaces");
        Long knowledgeSpaceUpdatePermissionId = ensurePermission("knowledge:space:update", "知识空间更新", "API", "/api/knowledge-spaces/**");
        Long knowledgeSpaceDeletePermissionId = ensurePermission("knowledge:space:delete", "知识空间删除", "API", "/api/knowledge-spaces/**");
        Long knowledgeDocCreatePermissionId = ensurePermission("knowledge:doc:create", "知识文档创建", "API", "/api/knowledge-docs");
        Long knowledgeDocUpdatePermissionId = ensurePermission("knowledge:doc:update", "知识文档更新", "API", "/api/knowledge-docs/**");
        Long knowledgeDocDeletePermissionId = ensurePermission("knowledge:doc:delete", "知识文档删除", "API", "/api/knowledge-docs/**");
        Long knowledgeDocPublishPermissionId = ensurePermission("knowledge:doc:publish", "知识文档发布", "API", "/api/knowledge-docs/*/publish");
        Long knowledgeDocRestorePermissionId = ensurePermission("knowledge:doc:restore", "知识版本回滚", "API", "/api/knowledge-docs/*/restore/*");
        Long knowledgeFavoritePermissionId = ensurePermission("knowledge:favorite", "知识收藏", "API", "/api/knowledge-favorites/**");
        Long knowledgeTagPermissionId = ensurePermission("knowledge:tag", "知识标签", "API", "/api/knowledge-tags/**");
        Long fileViewPermissionId = ensurePermission("file:view", "文件查看", "API", "/api/files/**");
        Long fileUploadPermissionId = ensurePermission("file:upload", "文件上传", "API", "/api/files/**");
        Long fileUpdatePermissionId = ensurePermission("file:update", "文件更新", "API", "/api/files/**");
        Long fileDeletePermissionId = ensurePermission("file:delete", "文件删除", "API", "/api/files/**");
        Long fileSharePermissionId = ensurePermission("file:share", "文件分享", "API", "/api/file-shares/**");
        Long aiViewPermissionId = ensurePermission("ai:view", "AI助手查看", "API", "/api/ai*/**");
        Long aiChatPermissionId = ensurePermission("ai:chat", "AI聊天", "API", "/api/ai/**");
        Long aiSessionPermissionId = ensurePermission("ai:session", "AI会话管理", "API", "/api/ai-sessions/**");
        Long aiMessagePermissionId = ensurePermission("ai:message", "AI消息管理", "API", "/api/ai-messages/**");
        Long aiEmbeddingPermissionId = ensurePermission("ai:embedding", "AI向量管理", "API", "/api/ai-embeddings/**");
        Long notificationViewPermissionId = ensurePermission("notification:view", "通知查看", "API", "/api/notifications/**");
        Long notificationCreatePermissionId = ensurePermission("notification:create", "通知创建", "API", "/api/notifications");
        Long notificationReadPermissionId = ensurePermission("notification:read", "通知已读", "API", "/api/notifications/*/read");
        Long notificationDeletePermissionId = ensurePermission("notification:delete", "通知删除", "API", "/api/notifications/**");
        Long systemManagePermissionId = ensurePermission("system:manage", "系统管理目录", "MENU", "/system");
        Long userViewPermissionId = ensurePermission("system:user:view", "用户查看", "API", "/api/users/**");
        Long userCreatePermissionId = ensurePermission("system:user:create", "用户创建", "API", "/api/users");
        Long userUpdatePermissionId = ensurePermission("system:user:update", "用户分配角色", "API", "/api/users/*/roles");
        Long roleViewPermissionId = ensurePermission("system:role:view", "角色查看", "API", "/api/roles/**");
        Long roleCreatePermissionId = ensurePermission("system:role:create", "角色创建", "API", "/api/roles");
        Long roleUpdatePermissionId = ensurePermission("system:role:update", "角色更新", "API", "/api/roles/**");
        Long roleDeletePermissionId = ensurePermission("system:role:delete", "角色删除", "API", "/api/roles/**");
        Long permissionViewPermissionId = ensurePermission("system:permission:view", "权限查看", "API", "/api/permissions/**");
        Long permissionCreatePermissionId = ensurePermission("system:permission:create", "权限创建", "API", "/api/permissions");
        Long permissionUpdatePermissionId = ensurePermission("system:permission:update", "权限更新", "API", "/api/permissions/**");
        Long permissionDeletePermissionId = ensurePermission("system:permission:delete", "权限删除", "API", "/api/permissions/**");
        Long menuViewPermissionId = ensurePermission("system:menu:view", "菜单查看", "API", "/api/menus/**");
        Long menuCreatePermissionId = ensurePermission("system:menu:create", "菜单创建", "API", "/api/menus");
        Long menuUpdatePermissionId = ensurePermission("system:menu:update", "菜单更新", "API", "/api/menus/**");
        Long menuDeletePermissionId = ensurePermission("system:menu:delete", "菜单删除", "API", "/api/menus/**");
        Long adminId = ensureAdminUser();
        Long devId = ensureDemoUser();
        Long demoViewerId = ensureDemoViewerUser();
        for (Long permissionId : List.of(
                dashboardPermissionId,
                teamViewPermissionId,
                teamCreatePermissionId,
                projectViewPermissionId,
                projectCreatePermissionId,
                projectUpdatePermissionId,
                projectDeletePermissionId,
                projectMemberPermissionId,
                projectTagPermissionId,
                taskViewPermissionId,
                taskCreatePermissionId,
                taskUpdatePermissionId,
                taskDeletePermissionId,
                taskCommentPermissionId,
                taskWorklogPermissionId,
                taskAttachmentPermissionId,
                taskTagPermissionId,
                knowledgeViewPermissionId,
                knowledgeSpaceCreatePermissionId,
                knowledgeSpaceUpdatePermissionId,
                knowledgeSpaceDeletePermissionId,
                knowledgeDocCreatePermissionId,
                knowledgeDocUpdatePermissionId,
                knowledgeDocDeletePermissionId,
                knowledgeDocPublishPermissionId,
                knowledgeDocRestorePermissionId,
                knowledgeFavoritePermissionId,
                knowledgeTagPermissionId,
                fileViewPermissionId,
                fileUploadPermissionId,
                fileUpdatePermissionId,
                fileDeletePermissionId,
                fileSharePermissionId,
                aiViewPermissionId,
                aiChatPermissionId,
                aiSessionPermissionId,
                aiMessagePermissionId,
                aiEmbeddingPermissionId,
                notificationViewPermissionId,
                notificationCreatePermissionId,
                notificationReadPermissionId,
                notificationDeletePermissionId,
                systemManagePermissionId,
                userViewPermissionId,
                userCreatePermissionId,
                userUpdatePermissionId,
                roleViewPermissionId,
                roleCreatePermissionId,
                roleUpdatePermissionId,
                roleDeletePermissionId,
                permissionViewPermissionId,
                permissionCreatePermissionId,
                permissionUpdatePermissionId,
                permissionDeletePermissionId,
                menuViewPermissionId,
                menuCreatePermissionId,
                menuUpdatePermissionId,
                menuDeletePermissionId
        )) {
            ensureRolePermission(adminRoleId, permissionId);
        }
        ensureRolePermission(developerRoleId, dashboardPermissionId);
        ensureRolePermission(developerRoleId, teamViewPermissionId);
        ensureRolePermission(developerRoleId, projectViewPermissionId);
        ensureRolePermission(developerRoleId, taskViewPermissionId);
        ensureRolePermission(developerRoleId, taskUpdatePermissionId);
        ensureRolePermission(developerRoleId, taskCommentPermissionId);
        ensureRolePermission(developerRoleId, taskWorklogPermissionId);
        ensureRolePermission(developerRoleId, knowledgeViewPermissionId);
        ensureRolePermission(developerRoleId, knowledgeDocUpdatePermissionId);
        ensureRolePermission(developerRoleId, knowledgeDocPublishPermissionId);
        ensureRolePermission(developerRoleId, knowledgeFavoritePermissionId);
        ensureRolePermission(developerRoleId, fileViewPermissionId);
        ensureRolePermission(developerRoleId, fileUploadPermissionId);
        ensureRolePermission(developerRoleId, fileSharePermissionId);
        ensureRolePermission(developerRoleId, aiViewPermissionId);
        ensureRolePermission(developerRoleId, aiChatPermissionId);
        ensureRolePermission(developerRoleId, aiSessionPermissionId);
        ensureRolePermission(developerRoleId, notificationViewPermissionId);
        ensureRolePermission(developerRoleId, notificationReadPermissionId);
        replaceRolePermissions(demoViewerRoleId, List.of(
                dashboardPermissionId,
                teamViewPermissionId,
                projectViewPermissionId,
                taskViewPermissionId,
                knowledgeViewPermissionId,
                fileViewPermissionId,
                aiViewPermissionId,
                aiChatPermissionId,
                notificationViewPermissionId,
                systemManagePermissionId,
                userViewPermissionId,
                roleViewPermissionId,
                permissionViewPermissionId,
                menuViewPermissionId
        ));
        ensureUserRole(adminId, adminRoleId);
        ensureUserRole(devId, developerRoleId);
        ensureExclusiveUserRole(demoViewerId, demoViewerRoleId);

        ensureMenu(0L, "工作台", "/dashboard", "DashboardView", "DataBoard", "dashboard:view", "MENU", 10);
        ensureMenu(0L, "项目", "/project/list", "ProjectListView", "Folder", "project:view", "MENU", 20);
        Long taskMenuId = ensureMenu(0L, "任务", "/task", "", "Checked", "task:view", "DIR", 30);
        ensureMenu(taskMenuId, "任务看板", "/task/board", "TaskBoardView", "Grid", "task:view", "MENU", 10);
        ensureMenu(taskMenuId, "任务列表", "/task/list", "TaskListView", "Tickets", "task:view", "MENU", 20);
        ensureMenu(taskMenuId, "甘特图", "/task/gantt", "TaskGanttView", "Histogram", "task:view", "MENU", 30);
        ensureMenu(0L, "知识库", "/knowledge", "KnowledgeBaseView", "Notebook", "knowledge:view", "MENU", 40);
        ensureMenu(0L, "文件中心", "/file/list", "FileCenterView", "Files", "file:view", "MENU", 50);
        ensureMenu(0L, "AI助手", "/ai/chat", "AiChatView", "ChatDotRound", "ai:view", "MENU", 60);
        ensureMenu(0L, "通知中心", "/notification", "NotificationCenterView", "Bell", "notification:view", "MENU", 70);
        Long systemMenuId = ensureMenu(0L, "系统管理", "/system", "", "Setting", "system:manage", "DIR", 90);
        ensureMenu(systemMenuId, "用户管理", "/system/user", "UserManagementView", "User", "system:user:view", "MENU", 10);
        ensureMenu(systemMenuId, "角色管理", "/system/role", "RoleManagementView", "UserFilled", "system:role:view", "MENU", 20);
        ensureMenu(systemMenuId, "权限管理", "/system/permission", "PermissionManagementView", "Key", "system:permission:view", "MENU", 30);
        ensureMenu(systemMenuId, "菜单管理", "/system/menu", "MenuManagementView", "Menu", "system:menu:view", "MENU", 40);
        ensureDashboardDemoData(adminId);
        ensureNotificationDemoData(adminId, devId, demoViewerId);
        markBootstrapCompleted("fresh-install");
        log.info("首次部署演示初始化数据已完成");
    }

    private boolean bootstrapMarkerExists() {
        return exists("SELECT 1 FROM sys_bootstrap_marker WHERE marker_key = ? LIMIT 1", BOOTSTRAP_MARKER_KEY);
    }

    private boolean hasExistingApplicationData() {
        return exists("SELECT 1 FROM sys_user WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM team WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM project WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM `task` WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM knowledge_space WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM file_info WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM ai_session WHERE deleted = 0 LIMIT 1")
                || exists("SELECT 1 FROM notification WHERE deleted = 0 LIMIT 1");
    }

    private void markBootstrapCompleted(String reason) {
        jdbcTemplate.update("""
                INSERT INTO sys_bootstrap_marker(marker_key, marker_value, created_at, updated_at)
                VALUES (?, ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE marker_value = VALUES(marker_value), updated_at = NOW()
                """, BOOTSTRAP_MARKER_KEY, BOOTSTRAP_MARKER_VALUE + ":" + reason);
    }

    private boolean exists(String sql, Object... args) {
        return !jdbcTemplate.query(sql, (rs, rowNum) -> 1, args).isEmpty();
    }

    private Long ensureRole(String code, String name, int sortNo, String remark) {
        jdbcTemplate.update("""
                INSERT INTO sys_role(role_code, role_name, scope_type, sort_no, status, remark, created_at, updated_at, deleted)
                SELECT ?, ?, 'SYSTEM', ?, 1, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = ? AND deleted = 0)
                """, code, name, sortNo, remark, code);
        return queryRequiredLong("SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0 LIMIT 1", code);
    }

    private Long ensurePermission(String code, String name, String type, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission(permission_code, permission_name, resource_type, resource_path, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ? AND deleted = 0)
                """, code, name, type, path, code);
        return queryRequiredLong("SELECT id FROM sys_permission WHERE permission_code = ? AND deleted = 0 LIMIT 1", code);
    }

    private Long ensureAdminUser() {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, DEFAULT_ADMIN_USERNAME)
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUser();
            user.setUsername(DEFAULT_ADMIN_USERNAME);
            user.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            user.setNickname("系统管理员");
            user.setEmail("admin@teamflow.local");
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setDeleted(0);
            userMapper.insert(user);
            return user.getId();
        }
        if (user.getPassword() == null || user.getPassword().contains("replace_with_bcrypt")) {
            user.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        }
        user.setStatus(1);
        user.setDeleted(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user.getId();
    }

    private Long ensureDemoUser() {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "dev")
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUser();
            user.setUsername("dev");
            user.setPassword(passwordEncoder.encode("dev123456"));
            user.setNickname("开发工程师");
            user.setEmail("dev@teamflow.local");
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setDeleted(0);
            userMapper.insert(user);
            return user.getId();
        }
        if (user.getPassword() == null || user.getPassword().contains("replace_with_bcrypt")) {
            user.setPassword(passwordEncoder.encode("dev123456"));
        }
        user.setStatus(1);
        user.setDeleted(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user.getId();
    }

    private Long ensureDemoViewerUser() {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, DemoAccountConstants.USERNAME)
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUser();
            user.setUsername(DemoAccountConstants.USERNAME);
            user.setPassword(passwordEncoder.encode(DemoAccountConstants.PASSWORD));
            user.setNickname("只读演示账号");
            user.setEmail("demo@teamflow.local");
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setDeleted(0);
            userMapper.insert(user);
            return user.getId();
        }
        if (user.getPassword() == null || user.getPassword().contains("replace_with_bcrypt")) {
            user.setPassword(passwordEncoder.encode(DemoAccountConstants.PASSWORD));
        }
        user.setNickname("只读演示账号");
        user.setEmail("demo@teamflow.local");
        user.setStatus(1);
        user.setDeleted(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user.getId();
    }

    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        permissionIds.stream().distinct().forEach(permissionId -> ensureRolePermission(roleId, permissionId));
    }

    private void ensureRolePermission(Long roleId, Long permissionId) {
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission(role_id, permission_id, created_at)
                SELECT ?, ?, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = ? AND permission_id = ?)
                """, roleId, permissionId, roleId, permissionId);
    }

    private void ensureExclusiveUserRole(Long userId, Long roleId) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ? AND (role_id IS NULL OR role_id <> ?)", userId, roleId);
        ensureUserRole(userId, roleId);
    }

    private void ensureUserRole(Long userId, Long roleId) {
        jdbcTemplate.update("""
                INSERT INTO sys_user_role(user_id, role_id, scope_type, scope_id, created_at)
                SELECT ?, ?, 'SYSTEM', 0, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = ? AND role_id = ?)
                """, userId, roleId, userId, roleId);
    }

    private Long ensureMenu(Long parentId, String name, String path, String component, String icon, String permissionCode, String type, int sortNo) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu(parent_id, menu_name, path, component, icon, permission_code, menu_type, sort_no, visible, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = ? AND deleted = 0)
                """, parentId, name, path, component, icon, permissionCode, type, sortNo, path);
        return queryRequiredLong("SELECT id FROM sys_menu WHERE path = ? AND deleted = 0 LIMIT 1", path);
    }

    private void ensureDashboardDemoData(Long adminId) {
        jdbcTemplate.update("""
                INSERT INTO team(team_name, team_code, owner_id, description, status, created_at, updated_at, deleted)
                SELECT 'TeamFlow 核心团队', 'TF-DEMO', ?, '用于面试演示的默认团队', 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM team WHERE team_code = 'TF-DEMO' AND deleted = 0)
                """, adminId);
        Long teamId = queryRequiredLong("SELECT id FROM team WHERE team_code = 'TF-DEMO' AND deleted = 0 LIMIT 1");
        jdbcTemplate.update("""
                INSERT INTO project(team_id, project_code, project_name, description, owner_id, start_date, end_date, status, progress, created_at, updated_at, deleted)
                SELECT ?, 'TF-AI', 'TeamFlow AI 企业协同平台', '包含认证、项目、任务、知识库、AI助手的协同平台', ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'ACTIVE', 35.00, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM project WHERE project_code = 'TF-AI' AND deleted = 0)
                """, teamId, adminId);
        Long projectId = queryRequiredLong("SELECT id FROM project WHERE project_code = 'TF-AI' AND deleted = 0 LIMIT 1");
        ensureProjectMember(projectId, adminId, "PM");
        ensureProjectTag(projectId, "AI协同", "#2563EB");
        ensureProjectTag(projectId, "面试演示", "#7C3AED");
        Long task1Id = ensureTask(projectId, adminId, "TF-1", "完成登录认证闭环", "DONE", 10);
        Long task2Id = ensureTask(projectId, adminId, "TF-2", "实现 RBAC 动态菜单", "DOING", 20);
        Long task3Id = ensureTask(projectId, adminId, "TF-3", "设计任务看板拖拽", "TODO", 30);
        Long task4Id = ensureTask(projectId, adminId, "TF-4", "接入 AI 知识库问答", "TESTING", 40);
        ensureTaskExecutor(task1Id, adminId);
        ensureTaskExecutor(task2Id, adminId);
        ensureTaskExecutor(task3Id, adminId);
        ensureTaskExecutor(task4Id, adminId);
        ensureTaskTag(task1Id, "认证", "#10B981");
        ensureTaskTag(task2Id, "权限", "#2563EB");
        ensureTaskTag(task3Id, "看板", "#7C3AED");
        ensureTaskTag(task4Id, "AI", "#F59E0B");
        ensureTaskComment(task3Id, adminId, "拖拽状态需要实时持久化，用于 Sprint 4 验收。");
        ensureTaskWorklog(task2Id, adminId, 2.5, "完成权限菜单联调");
        ensureKnowledgeDoc(teamId, adminId);
        fileService.ensureLocalDemoFile(adminId);
        ensureAiMessage(adminId);
    }

    private Long ensureTask(Long projectId, Long adminId, String taskNo, String title, String status, int sortNo) {
        jdbcTemplate.update("""
                INSERT INTO `task`(project_id, task_no, title, description, assignee_id, reporter_id, priority, status, sort_no, created_at, updated_at, deleted)
                SELECT ?, ?, ?, ?, ?, ?, 'HIGH', ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM `task` WHERE task_no = ? AND deleted = 0)
                """, projectId, taskNo, title, title, adminId, adminId, status, sortNo, taskNo);
        return queryRequiredLong("SELECT id FROM `task` WHERE task_no = ? AND deleted = 0 LIMIT 1", taskNo);
    }

    private void ensureTaskTag(Long taskId, String tagName, String color) {
        jdbcTemplate.update("""
                INSERT INTO task_tag(task_id, tag_name, tag_color, created_at)
                SELECT ?, ?, ?, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM task_tag WHERE task_id = ? AND tag_name = ?)
                """, taskId, tagName, color, taskId, tagName);
    }

    private void ensureTaskExecutor(Long taskId, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO task_executor(task_id, user_id, created_at)
                SELECT ?, ?, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM task_executor WHERE task_id = ? AND user_id = ?)
                """, taskId, userId, taskId, userId);
    }

    private void ensureTaskComment(Long taskId, Long userId, String content) {
        jdbcTemplate.update("""
                INSERT INTO task_comment(task_id, user_id, content, created_at, updated_at, deleted)
                SELECT ?, ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM task_comment WHERE task_id = ? AND content = ? AND deleted = 0)
                """, taskId, userId, content, taskId, content);
    }

    private void ensureTaskWorklog(Long taskId, Long userId, double hours, String description) {
        jdbcTemplate.update("""
                INSERT INTO task_worklog(task_id, user_id, work_date, hours, description, created_at, updated_at, deleted)
                SELECT ?, ?, CURDATE(), ?, ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM task_worklog WHERE task_id = ? AND description = ? AND deleted = 0)
                """, taskId, userId, hours, description, taskId, description);
        jdbcTemplate.update("""
                UPDATE `task`
                SET actual_hours = (
                    SELECT COALESCE(SUM(hours), 0)
                    FROM task_worklog
                    WHERE task_id = ? AND deleted = 0
                )
                WHERE id = ?
                """, taskId, taskId);
    }

    private void ensureProjectMember(Long projectId, Long userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO project_member(project_id, user_id, project_role, created_at, deleted)
                SELECT ?, ?, ?, NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM project_member WHERE project_id = ? AND user_id = ? AND deleted = 0)
                """, projectId, userId, role, projectId, userId);
    }

    private void ensureProjectTag(Long projectId, String tagName, String color) {
        jdbcTemplate.update("""
                INSERT INTO project_tag(project_id, tag_name, tag_color, created_at)
                SELECT ?, ?, ?, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM project_tag WHERE project_id = ? AND tag_name = ?)
                """, projectId, tagName, color, projectId, tagName);
    }

    private void ensureKnowledgeDoc(Long teamId, Long adminId) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_space(team_id, space_name, description, visibility, owner_id, created_at, updated_at, deleted)
                SELECT ?, '产品知识库', '用于 AI RAG 演示的知识空间', 'TEAM', ?, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM knowledge_space WHERE space_name = '产品知识库' AND deleted = 0)
                """, teamId, adminId);
        Long spaceId = queryRequiredLong("SELECT id FROM knowledge_space WHERE space_name = '产品知识库' AND deleted = 0 LIMIT 1");
        jdbcTemplate.update("""
                INSERT INTO knowledge_doc(space_id, parent_id, title, content_md, content_text, author_id, doc_status, sort_no, version_no, created_at, updated_at, deleted)
                SELECT ?, 0, 'TeamFlow AI 面试讲解提纲', '## TeamFlow AI\\n\\n认证、RBAC、协作、知识库和 AI 助手按模块递进实现。', 'TeamFlow AI 认证 RBAC 协作 知识库 AI 助手', ?, 'PUBLISHED', 10, 1, NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM knowledge_doc WHERE title = 'TeamFlow AI 面试讲解提纲' AND deleted = 0)
                """, spaceId, adminId);
        Long docId = queryRequiredLong("SELECT id FROM knowledge_doc WHERE title = 'TeamFlow AI 面试讲解提纲' AND deleted = 0 LIMIT 1");
        jdbcTemplate.update("""
                INSERT INTO knowledge_doc_version(doc_id, version_no, title, content_md, editor_id, change_summary, created_at)
                SELECT ?, 1, 'TeamFlow AI 面试讲解提纲', '## TeamFlow AI\\n\\n认证、RBAC、协作、知识库和 AI 助手按模块递进实现。', ?, '初始化发布版本', NOW()
                WHERE NOT EXISTS (SELECT 1 FROM knowledge_doc_version WHERE doc_id = ? AND version_no = 1)
                """, docId, adminId, docId);
        jdbcTemplate.update("""
                INSERT INTO knowledge_tag(doc_id, tag_name, created_at)
                SELECT ?, '面试演示', NOW()
                WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE doc_id = ? AND tag_name = '面试演示')
                """, docId, docId);
        jdbcTemplate.update("""
                INSERT INTO knowledge_tag(doc_id, tag_name, created_at)
                SELECT ?, '架构讲解', NOW()
                WHERE NOT EXISTS (SELECT 1 FROM knowledge_tag WHERE doc_id = ? AND tag_name = '架构讲解')
                """, docId, docId);
    }

    private void ensureAiMessage(Long adminId) {
        jdbcTemplate.update("""
                INSERT INTO ai_session(user_id, session_title, model_name, session_type, created_at, updated_at, deleted)
                SELECT ?, '默认演示会话', 'mock-ai', 'CHAT', NOW(), NOW(), 0
                WHERE NOT EXISTS (SELECT 1 FROM ai_session WHERE session_title = '默认演示会话' AND deleted = 0)
                """, adminId);
        Long sessionId = queryRequiredLong("SELECT id FROM ai_session WHERE session_title = '默认演示会话' AND deleted = 0 LIMIT 1");
        jdbcTemplate.update("""
                INSERT INTO ai_message(session_id, role, content, tokens, created_at)
                SELECT ?, 'ASSISTANT', 'MockAIProvider 已就绪，未配置 API Key 时也可以完整演示。', 32, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM ai_message WHERE session_id = ? AND role = 'ASSISTANT')
                """, sessionId, sessionId);
    }

    private void ensureNotificationDemoData(Long adminId, Long devId, Long demoViewerId) {
        ensureNotification(adminId, adminId, "系统初始化完成", "TeamFlow AI 通知中心已启用，支持未读状态和 WebSocket 实时推送。", "SYSTEM");
        ensureNotification(adminId, devId, "任务看板待验收", "请查看 Sprint 4 看板拖拽和任务详情演示数据。", "TASK");
        ensureNotification(adminId, demoViewerId, "只读演示账号已启用", "当前账号仅可浏览数据，所有新增、编辑、删除和状态变更请求都会被后端拦截。", "SYSTEM");
    }

    private void ensureNotification(Long senderId, Long targetId, String title, String content, String notifyType) {
        jdbcTemplate.update("""
                INSERT INTO notification(title, content, notify_type, target_type, target_id, sender_id, created_at, deleted)
                SELECT ?, ?, ?, 'USER', ?, ?, NOW(), 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM notification
                    WHERE title = ? AND target_type = 'USER' AND target_id = ? AND deleted = 0
                )
                """, title, content, notifyType, targetId, senderId, title, targetId);
    }

    private Long queryRequiredLong(String sql, Object... args) {
        List<Long> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        if (values.isEmpty()) {
            throw new IllegalStateException("初始化数据失败: " + sql);
        }
        return values.get(0);
    }
}

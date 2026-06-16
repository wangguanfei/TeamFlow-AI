package com.teamflow.ai.modules.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.project.entity.Project;
import com.teamflow.ai.modules.project.mapper.ProjectMapper;
import com.teamflow.ai.modules.task.dto.TaskDetail;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.dto.TaskRequest;
import com.teamflow.ai.modules.task.service.TaskService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TaskAgentTools {

    @Component
    public static class ListMyTasksTool implements AgentTool {

        private final TaskService taskService;

        public ListMyTasksTool(TaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "list_my_tasks",
                    "查询我的任务",
                    "查询当前用户负责或参与的待办事项，适合回答今天有哪些任务、我的待办、逾期事项等问题。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "status", Map.of("type", "string", "description", "任务状态，例如 TODO/DOING/TESTING/DONE/CLOSED"),
                                    "keyword", Map.of("type", "string", "description", "任务标题或描述关键词"),
                                    "limit", Map.of("type", "integer", "description", "返回条数，默认10")
                            )
                    ),
                    false,
                    "task:view"
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            String status = stringArg(arguments, "status");
            String keyword = stringArg(arguments, "keyword");
            int limit = intArg(arguments, "limit", 10);
            PageResult<TaskListItem> page = taskService.pageTasks(1, 100, null, normalizeBlank(status), normalizeBlank(keyword), null);
            List<Map<String, Object>> tasks = page.records().stream()
                    .filter(task -> Objects.equals(task.assigneeId(), user.getUserId()) || task.executorIds().contains(user.getUserId()))
                    .limit(Math.max(1, Math.min(limit, 20)))
                    .map(this::taskSummary)
                    .toList();
            String summary = tasks.isEmpty() ? "当前没有匹配的待办事项" : "找到 " + tasks.size() + " 条与你相关的待办事项";
            return ToolResult.ok(summary, Map.of("tasks", tasks, "count", tasks.size()), null, null, "/task/list");
        }

        private Map<String, Object> taskSummary(TaskListItem task) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", task.id());
            summary.put("taskNo", task.taskNo());
            summary.put("title", task.title());
            summary.put("projectName", task.projectName());
            summary.put("status", task.status());
            summary.put("priority", task.priority());
            summary.put("dueTime", task.dueTime());
            return summary;
        }
    }

    @Component
    public static class CreateTaskTool implements AgentTool {

        private final TaskService taskService;
        private final ProjectMapper projectMapper;
        private final SysUserMapper userMapper;

        public CreateTaskTool(TaskService taskService, ProjectMapper projectMapper, SysUserMapper userMapper) {
            this.taskService = taskService;
            this.projectMapper = projectMapper;
            this.userMapper = userMapper;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "create_task",
                    "创建待办事项",
                    "根据自然语言创建企业待办事项。写操作必须先返回预览，由用户确认后执行。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "title", Map.of("type", "string", "description", "待办标题"),
                                    "description", Map.of("type", "string", "description", "待办说明"),
                                    "projectId", Map.of("type", "integer", "description", "项目ID，可选；未指定时使用第一个可用项目"),
                                    "assigneeId", Map.of("type", "integer", "description", "负责人用户ID"),
                                    "assigneeName", Map.of("type", "string", "description", "负责人姓名或账号"),
                                    "dueDate", Map.of("type", "string", "description", "截止日期，格式 yyyy-MM-dd"),
                                    "priority", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH", "URGENT"))
                            ),
                            "required", List.of("title")
                    ),
                    true,
                    "task:create"
            );
        }

        @Override
        public Map<String, Object> preview(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTaskArgs resolved = resolve(arguments, user);
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("操作", "创建待办事项");
            preview.put("标题", resolved.title());
            preview.put("说明", resolved.description());
            preview.put("项目", resolved.project().getProjectName());
            preview.put("负责人", displayName(resolved.assignee()));
            preview.put("优先级", resolved.priority());
            preview.put("截止时间", resolved.dueTime());
            preview.put("提示", "确认前不会写入数据库。未指定项目时已使用第一个可用项目。");
            return preview;
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTaskArgs resolved = resolve(arguments, user);
            TaskRequest request = new TaskRequest(
                    resolved.project().getId(),
                    0L,
                    null,
                    resolved.title(),
                    resolved.description(),
                    resolved.assignee().getId(),
                    List.of(resolved.assignee().getId()),
                    resolved.priority(),
                    "TODO",
                    null,
                    resolved.dueTime(),
                    null,
                    1,
                    List.of()
            );
            TaskDetail detail = taskService.createTask(request, user.getUserId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", detail.task().id());
            data.put("taskNo", detail.task().taskNo());
            data.put("title", detail.task().title());
            data.put("projectName", detail.task().projectName());
            data.put("assigneeName", detail.task().assigneeName());
            data.put("dueTime", detail.task().dueTime());
            return ToolResult.ok("已创建待办事项「" + detail.task().title() + "」", data, "TASK", detail.task().id(), "/task/list?taskId=" + detail.task().id());
        }

        private ResolvedTaskArgs resolve(Map<String, Object> arguments, UserPrincipal user) {
            String title = stringArg(arguments, "title");
            if (title == null || title.isBlank()) {
                throw new BusinessException("创建待办事项需要标题");
            }
            Project project = resolveProject(longArg(arguments, "projectId"));
            SysUser assignee = resolveAssignee(longArg(arguments, "assigneeId"), stringArg(arguments, "assigneeName"), user.getUserId());
            return new ResolvedTaskArgs(
                    title.trim(),
                    stringArg(arguments, "description"),
                    project,
                    assignee,
                    normalizePriority(stringArg(arguments, "priority")),
                    parseDueTime(stringArg(arguments, "dueDate"))
            );
        }

        private Project resolveProject(Long projectId) {
            if (projectId != null) {
                Project project = projectMapper.selectById(projectId);
                if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
                    throw new BusinessException("项目不存在，无法创建待办");
                }
                return project;
            }
            List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .eq(Project::getDeleted, 0)
                    .orderByDesc(Project::getUpdatedAt)
                    .last("LIMIT 1"));
            if (projects.isEmpty()) {
                throw new BusinessException("当前没有可用项目，请先创建项目后再让 AI 创建待办");
            }
            return projects.get(0);
        }

        private SysUser resolveAssignee(Long assigneeId, String assigneeName, Long currentUserId) {
            if (assigneeId != null) {
                return getUser(assigneeId);
            }
            if (assigneeName != null && !assigneeName.isBlank()) {
                List<SysUser> exact = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeleted, 0)
                        .and(query -> query.eq(SysUser::getUsername, assigneeName).or().eq(SysUser::getNickname, assigneeName))
                        .last("LIMIT 2"));
                if (exact.size() == 1) {
                    return exact.get(0);
                }
                if (exact.size() > 1) {
                    throw new BusinessException("找到多个同名负责人，请明确用户账号");
                }
                List<SysUser> fuzzy = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeleted, 0)
                        .and(query -> query.like(SysUser::getUsername, assigneeName).or().like(SysUser::getNickname, assigneeName))
                        .last("LIMIT 2"));
                if (fuzzy.size() == 1) {
                    return fuzzy.get(0);
                }
                if (fuzzy.size() > 1) {
                    throw new BusinessException("找到多个相似负责人，请明确用户账号");
                }
                throw new BusinessException("未找到负责人：" + assigneeName);
            }
            return getUser(currentUserId);
        }

        private SysUser getUser(Long id) {
            SysUser user = userMapper.selectById(id);
            if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
                throw new BusinessException("负责人不存在");
            }
            return user;
        }

        private LocalDateTime parseDueTime(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atTime(18, 0);
            } catch (Exception e) {
                throw new BusinessException("截止日期格式暂只支持 yyyy-MM-dd，请补充明确日期");
            }
        }

        private String normalizePriority(String priority) {
            if (priority == null || priority.isBlank()) return "MEDIUM";
            return switch (priority.trim().toUpperCase()) {
                case "LOW", "MEDIUM", "HIGH", "URGENT" -> priority.trim().toUpperCase();
                default -> "MEDIUM";
            };
        }

        private String displayName(SysUser user) {
            return user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname();
        }

        private record ResolvedTaskArgs(String title, String description, Project project, SysUser assignee,
                                        String priority, LocalDateTime dueTime) {
        }
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}

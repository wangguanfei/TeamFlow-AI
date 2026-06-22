package com.teamflow.ai.modules.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.project.entity.Project;
import com.teamflow.ai.modules.project.mapper.ProjectMapper;
import com.teamflow.ai.modules.task.dto.TaskAttachmentItem;
import com.teamflow.ai.modules.task.dto.TaskCommentItem;
import com.teamflow.ai.modules.task.dto.TaskDetail;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.dto.TaskRequest;
import com.teamflow.ai.modules.task.dto.TaskWorklogItem;
import com.teamflow.ai.modules.task.service.TaskService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与任务查询和任务创建相关的 Agent 工具集合。
 *
 * <p>这里的内部类都会注册成独立 Spring Bean。读工具可直接执行，写工具 create_task
 * 必须先生成预览，用户确认后才真正调用 TaskService 写库。</p>
 */
@Component
public class TaskAgentTools {

    /** 查询“我负责/参与”的任务，适合回答我的待办、今日事项、逾期任务这类问题。 */
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
            List<TaskListItem> matchedTasks = page.records().stream()
                    .filter(task -> Objects.equals(task.assigneeId(), user.getUserId()) || task.executorIds().contains(user.getUserId()))
                    .limit(Math.max(1, Math.min(limit, 20)))
                    .toList();
            List<Map<String, Object>> tasks = matchedTasks.stream()
                    .map(this::taskSummary)
                    .toList();
            String summary = tasks.isEmpty() ? "当前没有匹配的待办事项" : "找到 " + tasks.size() + " 条与你相关的待办事项";
            if (matchedTasks.size() == 1) {
                TaskListItem task = matchedTasks.get(0);
                return ToolResult.ok(summary, Map.of("tasks", tasks, "count", tasks.size()), "TASK", task.id(), "/task/list?taskId=" + task.id());
            }
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
            summary.put("assigneeName", task.assigneeName());
            summary.put("dueTime", task.dueTime());
            return summary;
        }
    }

    /** 精确诊断单个任务的进展，适合回答“TF-4 这个任务怎么样了”。 */
    @Component
    public static class TaskProgressDetailTool implements AgentTool {

        private final TaskService taskService;

        public TaskProgressDetailTool(TaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "task_progress_detail",
                    "单任务进展诊断",
                    "精确查询某个任务的当前进展、负责人、截止时间、最近评论、工时、附件、风险判断和下一步建议。适合回答“TF-4进展如何”“某个任务卡住了吗”这类单任务问题。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "taskId", Map.of("type", "integer", "description", "任务ID"),
                                    "taskNo", Map.of("type", "string", "description", "任务编号，例如 TF-4"),
                                    "keyword", Map.of("type", "string", "description", "任务标题或描述关键词")
                            )
                    ),
                    false,
                    "task:view"
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            TaskDetail detail = resolveTaskDetail(arguments);
            TaskListItem task = detail.task();
            Map<String, Object> data = buildProgressData(detail);
            String summary = "任务「" + task.taskNo() + " " + task.title() + "」当前状态为 "
                    + statusLabel(task.status()) + "，负责人 " + nullSafe(task.assigneeName())
                    + "，风险等级 " + riskLabel(String.valueOf(data.get("riskLevel"))) + "。";
            return ToolResult.ok(summary, data, "TASK", task.id(), "/task/list?taskId=" + task.id());
        }

        private TaskDetail resolveTaskDetail(Map<String, Object> arguments) {
            Long taskId = longArg(arguments, "taskId");
            if (taskId != null) {
                return taskService.getTask(taskId);
            }
            String taskNo = normalizeBlank(stringArg(arguments, "taskNo"));
            String keyword = normalizeBlank(stringArg(arguments, "keyword"));
            if (taskNo == null && keyword != null) {
                taskNo = extractTaskNo(keyword);
            }
            keyword = taskNo != null ? taskNo : keyword;
            if (keyword == null) {
                throw new BusinessException("请提供任务ID、任务编号或任务标题关键词");
            }
            PageResult<TaskListItem> page = taskService.pageTasks(1, 20, null, null, keyword, null);
            String resolvedTaskNo = taskNo;
            List<TaskListItem> matches = resolvedTaskNo == null
                    ? page.records()
                    : page.records().stream().filter(task -> resolvedTaskNo.equalsIgnoreCase(task.taskNo())).toList();
            if (matches.isEmpty()) {
                throw new BusinessException("未找到匹配的任务：" + keyword);
            }
            if (matches.size() > 1) {
                throw new BusinessException("找到多个匹配任务，请提供更明确的任务编号或任务ID");
            }
            return taskService.getTask(matches.get(0).id());
        }

        private Map<String, Object> buildProgressData(TaskDetail detail) {
            TaskListItem task = detail.task();
            LocalDateTime now = LocalDateTime.now();
            boolean done = isDone(task);
            boolean overdue = !done && task.dueTime() != null && task.dueTime().isBefore(now);
            boolean dueToday = !done && task.dueTime() != null && task.dueTime().toLocalDate().equals(LocalDate.now());
            boolean highPriority = !done && ("HIGH".equals(task.priority()) || "URGENT".equals(task.priority()));
            long idleDays = task.updatedAt() == null ? 0 : Math.max(0, Duration.between(task.updatedAt(), now).toDays());
            boolean noProgressRecord = !done && detail.comments().isEmpty() && detail.worklogs().isEmpty();

            List<String> risks = new ArrayList<>();
            if (overdue) {
                risks.add("任务已逾期，截止时间为 " + task.dueTime());
            } else if (dueToday) {
                risks.add("任务今日到期，需要确认是否能按时完成");
            }
            if (highPriority) {
                risks.add(priorityLabel(task.priority()) + "任务尚未完成");
            }
            if (task.assigneeId() == null) {
                risks.add("任务未指定负责人");
            }
            if (idleDays >= 3 && !done) {
                risks.add("任务已 " + idleDays + " 天没有更新");
            }
            if (noProgressRecord) {
                risks.add("暂无评论或工时记录，缺少可追踪进展");
            }

            String riskLevel = overdue || "URGENT".equals(task.priority())
                    ? "HIGH"
                    : (highPriority || dueToday || idleDays >= 3 || noProgressRecord ? "MEDIUM" : "LOW");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", task.id());
            data.put("taskNo", task.taskNo());
            data.put("title", task.title());
            data.put("description", task.description());
            data.put("projectName", task.projectName());
            data.put("status", task.status());
            data.put("priority", task.priority());
            data.put("assigneeName", task.assigneeName());
            data.put("reporterName", task.reporterName());
            data.put("executorNames", task.executorNames());
            data.put("startTime", task.startTime());
            data.put("dueTime", task.dueTime());
            data.put("estimateHours", task.estimateHours());
            data.put("actualHours", task.actualHours());
            data.put("updatedAt", task.updatedAt());
            data.put("commentCount", detail.comments().size());
            data.put("worklogCount", detail.worklogs().size());
            data.put("attachmentCount", detail.attachments().size());
            data.put("recentComments", detail.comments().stream().limit(3).map(this::commentData).toList());
            data.put("recentWorklogs", detail.worklogs().stream().limit(3).map(this::worklogData).toList());
            data.put("recentAttachments", detail.attachments().stream().limit(3).map(this::attachmentData).toList());
            data.put("risks", risks);
            data.put("riskLevel", riskLevel);
            data.put("nextStep", nextStep(task, overdue, noProgressRecord));
            return data;
        }

        private Map<String, Object> commentData(TaskCommentItem comment) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("author", actorName(comment.nickname(), comment.username()));
            data.put("content", truncate(comment.content(), 120));
            data.put("createdAt", comment.createdAt());
            return data;
        }

        private Map<String, Object> worklogData(TaskWorklogItem worklog) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("author", actorName(worklog.nickname(), worklog.username()));
            data.put("workDate", worklog.workDate());
            data.put("hours", worklog.hours());
            data.put("description", truncate(worklog.description(), 100));
            data.put("createdAt", worklog.createdAt());
            return data;
        }

        private Map<String, Object> attachmentData(TaskAttachmentItem attachment) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileName", attachment.fileName());
            data.put("uploaderName", attachment.uploaderName());
            data.put("createdAt", attachment.createdAt());
            return data;
        }

        private String nextStep(TaskListItem task, boolean overdue, boolean noProgressRecord) {
            if (isDone(task)) {
                return "该任务已完成或关闭，可检查验收结果和沉淀资料是否齐全。";
            }
            if (task.assigneeId() == null) {
                return "建议先指定负责人，再继续推进。";
            }
            if (overdue) {
                return "建议立即催办负责人，并要求补充当前阻塞点和预计完成时间。";
            }
            if (noProgressRecord) {
                return "建议让负责人补充一条进展评论或登记工时，便于后续追踪。";
            }
            if ("TESTING".equals(task.status())) {
                return "建议关注验收反馈和缺陷处理，明确是否可以关闭任务。";
            }
            if ("DOING".equals(task.status())) {
                return "建议继续跟进负责人更新下一步交付物和预计完成时间。";
            }
            return "建议确认任务是否已经开始，并明确下一步处理时间。";
        }

        private boolean isDone(TaskListItem task) {
            return "DONE".equals(task.status()) || "CLOSED".equals(task.status());
        }

        private String actorName(String nickname, String username) {
            return nickname == null || nickname.isBlank() ? nullSafe(username) : nickname;
        }

        private String extractTaskNo(String value) {
            Matcher matcher = Pattern.compile("(?i)[A-Z][A-Z0-9]*-\\d+").matcher(value);
            return matcher.find() ? matcher.group().toUpperCase() : null;
        }

        private String statusLabel(String status) {
            return switch (status == null ? "" : status.toUpperCase()) {
                case "TODO" -> "待办";
                case "DOING" -> "进行中";
                case "TESTING" -> "测试中";
                case "DONE" -> "已完成";
                case "CLOSED" -> "已关闭";
                default -> nullSafe(status);
            };
        }

        private String priorityLabel(String priority) {
            return switch (priority == null ? "" : priority.toUpperCase()) {
                case "LOW" -> "低优先级";
                case "MEDIUM" -> "中优先级";
                case "HIGH" -> "高优先级";
                case "URGENT" -> "紧急";
                default -> nullSafe(priority);
            };
        }

        private String riskLabel(String riskLevel) {
            return switch (riskLevel == null ? "" : riskLevel.toUpperCase()) {
                case "HIGH" -> "高";
                case "MEDIUM" -> "中";
                case "LOW" -> "低";
                default -> "未知";
            };
        }

        private String nullSafe(String value) {
            return value == null || value.isBlank() ? "未指定" : value;
        }

        private String truncate(String value, int maxLength) {
            if (value == null || value.isBlank()) {
                return "-";
            }
            return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
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
            // 模型可能只给出标题。为了让“帮我记一个待办”顺畅落地，
            // 项目默认取最近更新的可用项目，负责人默认当前用户。
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
                // 先精确匹配 username/nickname，再模糊匹配；多结果时必须让用户明确，避免 AI 指派错人。
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

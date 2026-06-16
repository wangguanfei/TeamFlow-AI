package com.teamflow.ai.modules.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.notification.dto.NotificationItem;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.service.NotificationService;
import com.teamflow.ai.modules.task.dto.TaskDetail;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.dto.TaskRequest;
import com.teamflow.ai.modules.task.dto.TaskStatusRequest;
import com.teamflow.ai.modules.task.service.TaskService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowAgentTools {

    @Component
    public static class UpdateTaskStatusTool implements AgentTool {

        private final TaskService taskService;

        public UpdateTaskStatusTool(TaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "update_task_status",
                    "更新事项状态",
                    "根据任务ID、任务编号或标题关键词更新待办事项状态。写操作必须先返回预览，由用户确认后执行。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "taskId", Map.of("type", "integer", "description", "任务ID"),
                                    "taskNo", Map.of("type", "string", "description", "任务编号"),
                                    "keyword", Map.of("type", "string", "description", "任务标题关键词"),
                                    "status", Map.of("type", "string", "description", "目标状态，支持 TODO/DOING/TESTING/DONE/CLOSED，也支持中文")
                            ),
                            "required", List.of("status")
                    ),
                    true,
                    "task:update"
            );
        }

        @Override
        public Map<String, Object> preview(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTask task = resolveTask(taskService, arguments);
            String status = normalizeStatus(stringArg(arguments, "status"));
            Map<String, Object> preview = baseTaskPreview("更新事项状态", task.item());
            preview.put("原状态", task.item().status());
            preview.put("新状态", status);
            preview.put("提示", "确认前不会修改任务状态。");
            return preview;
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTask task = resolveTask(taskService, arguments);
            String status = normalizeStatus(stringArg(arguments, "status"));
            TaskListItem updated = taskService.updateStatus(task.item().id(), new TaskStatusRequest(status, task.item().sortNo()));
            Map<String, Object> data = baseTaskData(updated);
            data.put("status", updated.status());
            return ToolResult.ok("已将事项「" + updated.title() + "」更新为 " + updated.status(), data, "TASK", updated.id(), "/task/list?taskId=" + updated.id());
        }
    }

    @Component
    public static class AssignTaskTool implements AgentTool {

        private final TaskService taskService;
        private final SysUserMapper userMapper;

        public AssignTaskTool(TaskService taskService, SysUserMapper userMapper) {
            this.taskService = taskService;
            this.userMapper = userMapper;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "assign_task",
                    "指派负责人",
                    "将任务指派给指定负责人，并保留原有任务信息。写操作必须先返回预览，由用户确认后执行。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "taskId", Map.of("type", "integer", "description", "任务ID"),
                                    "taskNo", Map.of("type", "string", "description", "任务编号"),
                                    "keyword", Map.of("type", "string", "description", "任务标题关键词"),
                                    "assigneeId", Map.of("type", "integer", "description", "负责人用户ID"),
                                    "assigneeName", Map.of("type", "string", "description", "负责人姓名或账号")
                            )
                    ),
                    true,
                    "task:update"
            );
        }

        @Override
        public Map<String, Object> preview(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTask task = resolveTask(taskService, arguments);
            SysUser assignee = resolveUser(userMapper, longArg(arguments, "assigneeId"), stringArg(arguments, "assigneeName"));
            Map<String, Object> preview = baseTaskPreview("指派负责人", task.item());
            preview.put("原负责人", task.item().assigneeName());
            preview.put("新负责人", displayName(assignee));
            preview.put("提示", "确认后将更新任务负责人，并按现有任务逻辑发送负责人变更通知。");
            return preview;
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            TaskDetail detail = resolveTask(taskService, arguments).detail();
            SysUser assignee = resolveUser(userMapper, longArg(arguments, "assigneeId"), stringArg(arguments, "assigneeName"));
            TaskListItem task = detail.task();
            Set<Long> executorIds = new LinkedHashSet<>(task.executorIds());
            executorIds.add(assignee.getId());
            TaskRequest request = new TaskRequest(
                    task.projectId(),
                    task.parentId(),
                    task.taskNo(),
                    task.title(),
                    task.description(),
                    assignee.getId(),
                    executorIds.stream().toList(),
                    task.priority(),
                    task.status(),
                    task.startTime(),
                    task.dueTime(),
                    task.estimateHours(),
                    task.sortNo(),
                    List.of()
            );
            TaskDetail updated = taskService.updateTask(task.id(), request, user.getUserId());
            Map<String, Object> data = baseTaskData(updated.task());
            data.put("assigneeName", updated.task().assigneeName());
            return ToolResult.ok("已将事项「" + updated.task().title() + "」指派给 " + updated.task().assigneeName(), data, "TASK", updated.task().id(), "/task/list?taskId=" + updated.task().id());
        }
    }

    @Component
    public static class SendNotificationTool implements AgentTool {

        private final TaskService taskService;
        private final NotificationService notificationService;
        private final SysUserMapper userMapper;

        public SendNotificationTool(TaskService taskService, NotificationService notificationService, SysUserMapper userMapper) {
            this.taskService = taskService;
            this.notificationService = notificationService;
            this.userMapper = userMapper;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "send_notification",
                    "发送提醒/催办",
                    "围绕任务向指定人员发送站内提醒或催办通知。写操作必须先返回预览，由用户确认后执行。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "taskId", Map.of("type", "integer", "description", "任务ID"),
                                    "taskNo", Map.of("type", "string", "description", "任务编号"),
                                    "keyword", Map.of("type", "string", "description", "任务标题关键词"),
                                    "targetUserId", Map.of("type", "integer", "description", "接收人用户ID，默认任务负责人"),
                                    "targetUserName", Map.of("type", "string", "description", "接收人姓名或账号"),
                                    "message", Map.of("type", "string", "description", "提醒内容")
                            )
                    ),
                    true,
                    "notification:create"
            );
        }

        @Override
        public Map<String, Object> preview(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTask task = resolveTask(taskService, arguments);
            SysUser target = resolveReminderTarget(task.item(), arguments);
            Map<String, Object> preview = baseTaskPreview("发送提醒/催办", task.item());
            preview.put("接收人", displayName(target));
            preview.put("提醒内容", reminderContent(task.item(), stringArg(arguments, "message")));
            preview.put("提示", "确认后将发送站内通知。");
            return preview;
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            ResolvedTask task = resolveTask(taskService, arguments);
            SysUser target = resolveReminderTarget(task.item(), arguments);
            NotificationItem notification = notificationService.create(new NotificationRequest(
                    "任务催办：" + task.item().title(),
                    reminderContent(task.item(), stringArg(arguments, "message")),
                    "TASK",
                    "USER",
                    target.getId(),
                    "TASK",
                    task.item().id(),
                    task.item().dueTime()
            ), user.getUserId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("notificationId", notification.id());
            data.put("taskId", task.item().id());
            data.put("taskTitle", task.item().title());
            data.put("targetUserId", target.getId());
            data.put("targetUserName", displayName(target));
            return ToolResult.ok("已向 " + displayName(target) + " 发送事项提醒", data, "NOTIFICATION", notification.id(), "/notification");
        }

        private SysUser resolveReminderTarget(TaskListItem task, Map<String, Object> arguments) {
            Long explicitId = longArg(arguments, "targetUserId");
            String explicitName = stringArg(arguments, "targetUserName");
            if (explicitId != null || explicitName != null && !explicitName.isBlank()) {
                return resolveUser(userMapper, explicitId, explicitName);
            }
            return getUser(userMapper, task.assigneeId());
        }
    }

    @Component
    public static class ProjectSummaryTool implements AgentTool {

        private final TaskService taskService;

        public ProjectSummaryTool(TaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "query_project_summary",
                    "生成工作进展简报",
                    "基于项目或关键词统计任务状态、风险事项和近期进展，生成工作进展简报。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "projectId", Map.of("type", "integer", "description", "项目ID"),
                                    "keyword", Map.of("type", "string", "description", "项目或任务关键词"),
                                    "limit", Map.of("type", "integer", "description", "统计任务上限，默认100")
                            )
                    ),
                    false,
                    "task:view"
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            Long projectId = longArg(arguments, "projectId");
            String keyword = normalizeBlank(stringArg(arguments, "keyword"));
            int limit = boundedLimit(arguments, 100);
            List<TaskListItem> tasks = taskService.pageTasks(1, limit, projectId, null, keyword, null).records();
            Map<String, Object> data = buildTaskBrief(tasks);
            data.put("projectId", projectId);
            data.put("keyword", keyword);
            String summary = "工作进展简报：共 " + tasks.size() + " 个事项，完成 "
                    + data.get("doneCount") + " 个，进行中 " + data.get("doingCount")
                    + " 个，逾期 " + data.get("overdueCount") + " 个。";
            return ToolResult.ok(summary, data, projectId == null ? null : "PROJECT", projectId, "/task/list");
        }
    }

    @Component
    public static class DailyBusinessBriefTool implements AgentTool {

        private final TaskService taskService;

        public DailyBusinessBriefTool(TaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "daily_business_brief",
                    "老板每日经营简报",
                    "生成老板视角的每日经营简报MVP，覆盖任务总量、今日到期、逾期风险和关键事项。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "keyword", Map.of("type", "string", "description", "可选关键词"),
                                    "limit", Map.of("type", "integer", "description", "统计任务上限，默认200")
                            )
                    ),
                    false,
                    "task:view"
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments, UserPrincipal user) {
            String keyword = normalizeBlank(stringArg(arguments, "keyword"));
            int limit = boundedLimit(arguments, 200);
            List<TaskListItem> tasks = taskService.pageTasks(1, limit, null, null, keyword, null).records();
            Map<String, Object> data = buildTaskBrief(tasks);
            data.put("todayDueCount", tasks.stream().filter(WorkflowAgentTools::isDueToday).count());
            data.put("briefDate", LocalDate.now().toString());
            String summary = "老板每日经营简报：" + LocalDate.now()
                    + "，当前事项 " + tasks.size()
                    + " 个，今日到期 " + data.get("todayDueCount")
                    + " 个，逾期 " + data.get("overdueCount")
                    + " 个，需关注高优先级 " + data.get("highPriorityOpenCount") + " 个。";
            return ToolResult.ok(summary, data, null, null, "/dashboard");
        }
    }

    private static ResolvedTask resolveTask(TaskService taskService, Map<String, Object> arguments) {
        Long taskId = longArg(arguments, "taskId");
        if (taskId != null) {
            TaskDetail detail = taskService.getTask(taskId);
            return new ResolvedTask(detail.task(), detail);
        }
        String taskNo = normalizeBlank(stringArg(arguments, "taskNo"));
        String keyword = taskNo != null ? taskNo : normalizeBlank(stringArg(arguments, "keyword"));
        if (keyword == null) {
            throw new BusinessException("请提供任务ID、任务编号或任务标题关键词");
        }
        PageResult<TaskListItem> page = taskService.pageTasks(1, 20, null, null, keyword, null);
        List<TaskListItem> matches = taskNo == null
                ? page.records()
                : page.records().stream().filter(task -> taskNo.equalsIgnoreCase(task.taskNo())).toList();
        if (matches.isEmpty()) {
            throw new BusinessException("未找到匹配的任务：" + keyword);
        }
        if (matches.size() > 1) {
            throw new BusinessException("找到多个匹配任务，请提供更明确的任务ID或任务编号");
        }
        TaskDetail detail = taskService.getTask(matches.get(0).id());
        return new ResolvedTask(detail.task(), detail);
    }

    private static Map<String, Object> baseTaskPreview(String operation, TaskListItem task) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("操作", operation);
        preview.put("任务", task.title());
        preview.put("任务编号", task.taskNo());
        preview.put("项目", task.projectName());
        return preview;
    }

    private static Map<String, Object> baseTaskData(TaskListItem task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", task.id());
        data.put("taskNo", task.taskNo());
        data.put("title", task.title());
        data.put("projectName", task.projectName());
        return data;
    }

    private static Map<String, Object> buildTaskBrief(List<TaskListItem> tasks) {
        Map<String, Long> statusCounts = tasks.stream()
                .collect(Collectors.groupingBy(TaskListItem::status, LinkedHashMap::new, Collectors.counting()));
        long done = tasks.stream().filter(WorkflowAgentTools::isDone).count();
        long doing = tasks.stream().filter(task -> "DOING".equals(task.status()) || "TESTING".equals(task.status())).count();
        long overdue = tasks.stream().filter(WorkflowAgentTools::isOverdue).count();
        long highPriorityOpen = tasks.stream().filter(task -> !isDone(task) && ("HIGH".equals(task.priority()) || "URGENT".equals(task.priority()))).count();
        List<Map<String, Object>> riskTasks = tasks.stream()
                .filter(task -> isOverdue(task) || !isDone(task) && "URGENT".equals(task.priority()))
                .limit(5)
                .map(WorkflowAgentTools::briefTask)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", tasks.size());
        data.put("statusCounts", statusCounts);
        data.put("doneCount", done);
        data.put("doingCount", doing);
        data.put("overdueCount", overdue);
        data.put("highPriorityOpenCount", highPriorityOpen);
        data.put("riskTasks", riskTasks);
        data.put("recentTasks", tasks.stream().limit(8).map(WorkflowAgentTools::briefTask).toList());
        return data;
    }

    private static Map<String, Object> briefTask(TaskListItem task) {
        Map<String, Object> item = baseTaskData(task);
        item.put("status", task.status());
        item.put("priority", task.priority());
        item.put("assigneeName", task.assigneeName());
        item.put("dueTime", task.dueTime());
        return item;
    }

    private static boolean isDone(TaskListItem task) {
        return "DONE".equals(task.status()) || "CLOSED".equals(task.status());
    }

    private static boolean isOverdue(TaskListItem task) {
        return task.dueTime() != null && task.dueTime().isBefore(LocalDateTime.now()) && !isDone(task);
    }

    private static boolean isDueToday(TaskListItem task) {
        return task.dueTime() != null && task.dueTime().toLocalDate().equals(LocalDate.now()) && !isDone(task);
    }

    private static String reminderContent(TaskListItem task, String message) {
        String content = message == null || message.isBlank()
                ? "请及时跟进任务「" + task.title() + "」(" + task.taskNo() + ")。"
                : message.trim();
        if (task.dueTime() != null) {
            content += "\n截止时间：" + task.dueTime();
        }
        return content;
    }

    private static SysUser resolveUser(SysUserMapper userMapper, Long userId, String name) {
        if (userId != null) {
            return getUser(userMapper, userId);
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("请指定负责人或接收人");
        }
        List<SysUser> exact = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .and(query -> query.eq(SysUser::getUsername, name).or().eq(SysUser::getNickname, name))
                .last("LIMIT 2"));
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            throw new BusinessException("找到多个同名用户，请明确用户账号");
        }
        List<SysUser> fuzzy = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .and(query -> query.like(SysUser::getUsername, name).or().like(SysUser::getNickname, name))
                .last("LIMIT 2"));
        if (fuzzy.size() == 1) {
            return fuzzy.get(0);
        }
        if (fuzzy.size() > 1) {
            throw new BusinessException("找到多个相似用户，请明确用户账号");
        }
        throw new BusinessException("未找到用户：" + name);
    }

    private static SysUser getUser(SysUserMapper userMapper, Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private static String displayName(SysUser user) {
        return user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname();
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException("请指定目标状态");
        }
        return switch (status.trim().toUpperCase()) {
            case "TODO", "待办", "待处理", "未开始" -> "TODO";
            case "DOING", "进行中", "处理中", "开始" -> "DOING";
            case "TESTING", "测试", "测试中", "验收", "待验收" -> "TESTING";
            case "DONE", "完成", "已完成", "办完" -> "DONE";
            case "CLOSED", "关闭", "已关闭", "取消" -> "CLOSED";
            default -> throw new BusinessException("不支持的任务状态：" + status);
        };
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private static int boundedLimit(Map<String, Object> args, int fallback) {
        Object value = args == null ? null : args.get("limit");
        int limit = fallback;
        if (value instanceof Number number) {
            limit = number.intValue();
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                limit = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                limit = fallback;
            }
        }
        return Math.max(1, Math.min(limit, 300));
    }

    private record ResolvedTask(TaskListItem item, TaskDetail detail) {
    }
}

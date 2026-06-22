package com.teamflow.ai.modules.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.entity.Notification;
import com.teamflow.ai.modules.notification.mapper.NotificationMapper;
import com.teamflow.ai.modules.notification.service.NotificationService;
import com.teamflow.ai.modules.system.entity.SysRole;
import com.teamflow.ai.modules.system.entity.SysUserRole;
import com.teamflow.ai.modules.system.mapper.SysRoleMapper;
import com.teamflow.ai.modules.system.mapper.SysUserRoleMapper;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.service.TaskService;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 老板每日经营简报订阅 MVP。
 *
 * <p>首版不新增订阅表，默认按角色发送给管理者，借助 notification.bizType/targetId/createdAt
 * 做每日去重。后续若要做个人订阅页，可以把收件人解析替换为订阅表查询。</p>
 */
@Service
public class AgentDailyBriefService {

    private static final Logger log = LoggerFactory.getLogger(AgentDailyBriefService.class);
    private static final String TARGET_USER = "USER";
    private static final String NOTIFY_TYPE = "BUSINESS_BRIEF";
    private static final String BIZ_TYPE = "DAILY_BUSINESS_BRIEF";

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final boolean enabled;
    private final int maxTasks;
    private final List<String> roleCodes;
    private final LocalTime sendAfter;

    public AgentDailyBriefService(
            TaskService taskService,
            NotificationService notificationService,
            NotificationMapper notificationMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            SysUserMapper userMapper,
            @Value("${teamflow.agent.daily-brief.enabled:true}") boolean enabled,
            @Value("${teamflow.agent.daily-brief.max-tasks:300}") int maxTasks,
            @Value("${teamflow.agent.daily-brief.recipient-role-codes:SUPER_ADMIN}") String roleCodes,
            @Value("${teamflow.agent.daily-brief.send-after:08:30}") String sendAfter
    ) {
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.enabled = enabled;
        this.maxTasks = Math.max(50, Math.min(maxTasks, 1000));
        this.roleCodes = parseRoleCodes(roleCodes);
        this.sendAfter = parseSendAfter(sendAfter);
    }

    @Scheduled(
            initialDelayString = "${teamflow.agent.daily-brief.initial-delay-ms:180000}",
            fixedDelayString = "${teamflow.agent.daily-brief.fixed-delay-ms:3600000}"
    )
    public void scanScheduled() {
        if (!enabled || LocalTime.now().isBefore(sendAfter)) {
            return;
        }
        try {
            int sent = sendTodayBriefs();
            if (sent > 0) {
                log.info("AI每日经营简报发送完成，发送通知 {} 条", sent);
            }
        } catch (Exception ex) {
            log.warn("AI每日经营简报发送失败", ex);
        }
    }

    public int sendTodayBriefs() {
        List<SysUser> recipients = resolveRecipients();
        if (recipients.isEmpty()) {
            return 0;
        }
        PageResult<TaskListItem> page = taskService.pageTasks(1, maxTasks, null, null, null, null);
        BriefSnapshot snapshot = buildSnapshot(page.records());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        int sent = 0;
        for (SysUser recipient : recipients) {
            if (alreadySentToday(recipient.getId(), todayStart)) {
                continue;
            }
            notificationService.create(new NotificationRequest(
                    "老板每日经营简报：" + LocalDate.now(),
                    buildContent(snapshot),
                    NOTIFY_TYPE,
                    TARGET_USER,
                    recipient.getId(),
                    BIZ_TYPE,
                    recipient.getId(),
                    now
            ), null);
            sent++;
        }
        return sent;
    }

    private List<SysUser> resolveRecipients() {
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .eq(SysRole::getStatus, 1)
                .in(SysRole::getRoleCode, roleCodes));
        List<Long> roleIds = roles.stream().map(SysRole::getId).distinct().toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, roleIds))
                .stream()
                .map(SysUserRole::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(user -> user != null && !Integer.valueOf(1).equals(user.getDeleted()))
                .filter(user -> user.getStatus() == null || user.getStatus() == 1)
                .toList();
    }

    private boolean alreadySentToday(Long userId, LocalDateTime todayStart) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getDeleted, 0)
                .eq(Notification::getNotifyType, NOTIFY_TYPE)
                .eq(Notification::getBizType, BIZ_TYPE)
                .eq(Notification::getTargetType, TARGET_USER)
                .eq(Notification::getTargetId, userId)
                .ge(Notification::getCreatedAt, todayStart));
        return count != null && count > 0;
    }

    private BriefSnapshot buildSnapshot(List<TaskListItem> tasks) {
        long done = tasks.stream().filter(this::isDone).count();
        long doing = tasks.stream().filter(task -> "DOING".equals(task.status()) || "TESTING".equals(task.status())).count();
        long overdue = tasks.stream().filter(this::isOverdue).count();
        long dueToday = tasks.stream().filter(this::isDueToday).count();
        long highPriorityOpen = tasks.stream().filter(task -> !isDone(task) && ("HIGH".equals(task.priority()) || "URGENT".equals(task.priority()))).count();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (TaskListItem task : tasks) {
            statusCounts.merge(task.status() == null ? "UNSET" : task.status(), 1L, Long::sum);
        }
        List<TaskListItem> riskTasks = tasks.stream()
                .filter(task -> isOverdue(task) || !isDone(task) && "URGENT".equals(task.priority()) || isDueToday(task))
                .limit(5)
                .toList();
        return new BriefSnapshot(tasks.size(), done, doing, overdue, dueToday, highPriorityOpen, statusCounts, riskTasks);
    }

    private String buildContent(BriefSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("今日经营概览：")
                .append("当前事项 ").append(snapshot.total()).append(" 个，")
                .append("完成 ").append(snapshot.done()).append(" 个，")
                .append("进行中 ").append(snapshot.doing()).append(" 个，")
                .append("今日到期 ").append(snapshot.dueToday()).append(" 个，")
                .append("逾期 ").append(snapshot.overdue()).append(" 个，")
                .append("高优先级待跟进 ").append(snapshot.highPriorityOpen()).append(" 个。");
        if (!snapshot.statusCounts().isEmpty()) {
            builder.append("\n\n状态分布：");
            snapshot.statusCounts().forEach((status, count) ->
                    builder.append(statusLabel(status)).append(" ").append(count).append(" 个；"));
        }
        if (!snapshot.riskTasks().isEmpty()) {
            builder.append("\n\n重点风险：");
            int index = 1;
            for (TaskListItem task : snapshot.riskTasks()) {
                builder.append("\n").append(index++).append(". ")
                        .append(task.title()).append("（").append(task.taskNo()).append("，")
                        .append(statusLabel(task.status())).append("，负责人：")
                        .append(task.assigneeName() == null || task.assigneeName().isBlank() ? "未指定" : task.assigneeName());
                if (task.dueTime() != null) {
                    builder.append("，截止：").append(task.dueTime());
                }
                builder.append("）");
            }
        }
        builder.append("\n\n建议：优先处理逾期、今日到期和高优先级未完成事项。");
        return builder.toString();
    }

    private boolean isDone(TaskListItem task) {
        return "DONE".equals(task.status()) || "CLOSED".equals(task.status());
    }

    private boolean isOverdue(TaskListItem task) {
        return task.dueTime() != null && task.dueTime().isBefore(LocalDateTime.now()) && !isDone(task);
    }

    private boolean isDueToday(TaskListItem task) {
        return task.dueTime() != null && task.dueTime().toLocalDate().equals(LocalDate.now()) && !isDone(task);
    }

    private List<String> parseRoleCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private LocalTime parseSendAfter(String value) {
        try {
            return value == null || value.isBlank() ? LocalTime.of(8, 30) : LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            return LocalTime.of(8, 30);
        }
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "TODO" -> "待办";
            case "DOING" -> "进行中";
            case "TESTING" -> "测试中";
            case "DONE" -> "已完成";
            case "CLOSED" -> "已关闭";
            default -> status == null || status.isBlank() ? "未设置" : status;
        };
    }

    private record BriefSnapshot(
            int total,
            long done,
            long doing,
            long overdue,
            long dueToday,
            long highPriorityOpen,
            Map<String, Long> statusCounts,
            List<TaskListItem> riskTasks
    ) {
    }
}

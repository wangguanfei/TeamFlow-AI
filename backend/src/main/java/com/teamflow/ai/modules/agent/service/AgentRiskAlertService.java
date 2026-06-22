package com.teamflow.ai.modules.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.entity.Notification;
import com.teamflow.ai.modules.notification.mapper.NotificationMapper;
import com.teamflow.ai.modules.notification.service.NotificationService;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主动经营预警：把任务风险从“用户来问”前移到“系统主动提醒”。
 *
 * <p>当前 MVP 仅基于现有任务数据生成站内通知，不新增业务表。通知表已有
 * bizType/bizId/bizTime，可用于按天去重，避免定时任务重复刷屏。</p>
 */
@Service
public class AgentRiskAlertService {

    private static final Logger log = LoggerFactory.getLogger(AgentRiskAlertService.class);
    private static final String TARGET_USER = "USER";

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final boolean enabled;
    private final int maxTasks;
    private final int staleDays;

    public AgentRiskAlertService(
            TaskService taskService,
            NotificationService notificationService,
            NotificationMapper notificationMapper,
            @Value("${teamflow.agent.risk-alert.enabled:true}") boolean enabled,
            @Value("${teamflow.agent.risk-alert.max-tasks:300}") int maxTasks,
            @Value("${teamflow.agent.risk-alert.stale-days:3}") int staleDays
    ) {
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
        this.enabled = enabled;
        this.maxTasks = Math.max(50, Math.min(maxTasks, 1000));
        this.staleDays = Math.max(1, staleDays);
    }

    @Scheduled(
            initialDelayString = "${teamflow.agent.risk-alert.initial-delay-ms:120000}",
            fixedDelayString = "${teamflow.agent.risk-alert.fixed-delay-ms:3600000}"
    )
    public void scanScheduled() {
        if (!enabled) {
            return;
        }
        try {
            int sent = scanOnce();
            if (sent > 0) {
                log.info("AI主动经营预警扫描完成，发送通知 {} 条", sent);
            }
        } catch (Exception ex) {
            log.warn("AI主动经营预警扫描失败", ex);
        }
    }

    public int scanOnce() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<TaskListItem> tasks = taskService.pageTasks(1, maxTasks, null, null, null, null).records();
        int sent = 0;
        for (TaskListItem task : tasks) {
            RiskAlert alert = detectAlert(task, now);
            if (alert == null || task.assigneeId() == null) {
                continue;
            }
            if (alreadySentToday(alert.type(), task.id(), task.assigneeId(), todayStart)) {
                continue;
            }
            notificationService.create(new NotificationRequest(
                    alert.title(),
                    alert.content(),
                    "TASK_RISK",
                    TARGET_USER,
                    task.assigneeId(),
                    alert.type(),
                    task.id(),
                    task.dueTime()
            ), null);
            sent++;
        }
        return sent;
    }

    private RiskAlert detectAlert(TaskListItem task, LocalDateTime now) {
        if (isDone(task)) {
            return null;
        }
        if (task.dueTime() != null && task.dueTime().isBefore(now)) {
            return new RiskAlert(
                    "TASK_OVERDUE",
                    "任务已逾期：" + task.title(),
                    "任务「" + task.title() + "」(" + task.taskNo() + ") 已超过截止时间，请尽快更新进展或调整计划。\n"
                            + taskMeta(task)
            );
        }
        if (task.dueTime() != null && task.dueTime().toLocalDate().equals(LocalDate.now())) {
            return new RiskAlert(
                    "TASK_DUE_TODAY",
                    "任务今日到期：" + task.title(),
                    "任务「" + task.title() + "」(" + task.taskNo() + ") 今日到期，请确认是否能按时完成。\n"
                            + taskMeta(task)
            );
        }
        if (isHighPriority(task) && idleDays(task, now) >= staleDays) {
            return new RiskAlert(
                    "TASK_HIGH_PRIORITY_STALE",
                    "高优先级任务多日未更新：" + task.title(),
                    "高优先级任务「" + task.title() + "」(" + task.taskNo() + ") 已 "
                            + idleDays(task, now) + " 天未更新，请补充进展。\n" + taskMeta(task)
            );
        }
        return null;
    }

    private boolean alreadySentToday(String bizType, Long taskId, Long targetUserId, LocalDateTime todayStart) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getDeleted, 0)
                .eq(Notification::getBizType, bizType)
                .eq(Notification::getBizId, taskId)
                .eq(Notification::getTargetType, TARGET_USER)
                .eq(Notification::getTargetId, targetUserId)
                .ge(Notification::getCreatedAt, todayStart));
        return count != null && count > 0;
    }

    private boolean isDone(TaskListItem task) {
        return "DONE".equals(task.status()) || "CLOSED".equals(task.status());
    }

    private boolean isHighPriority(TaskListItem task) {
        return "HIGH".equals(task.priority()) || "URGENT".equals(task.priority());
    }

    private long idleDays(TaskListItem task, LocalDateTime now) {
        return task.updatedAt() == null ? 0 : Math.max(0, Duration.between(task.updatedAt(), now).toDays());
    }

    private String taskMeta(TaskListItem task) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前状态：").append(statusLabel(task.status()));
        builder.append("；负责人：").append(task.assigneeName() == null || task.assigneeName().isBlank() ? "未指定" : task.assigneeName());
        if (task.dueTime() != null) {
            builder.append("；截止时间：").append(task.dueTime());
        }
        return builder.toString();
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

    private record RiskAlert(String type, String title, String content) {
    }
}

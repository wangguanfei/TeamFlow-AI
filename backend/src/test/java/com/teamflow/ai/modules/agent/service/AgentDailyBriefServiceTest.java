package com.teamflow.ai.modules.agent.service;

import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDailyBriefServiceTest {

    @Test
    void sendsDailyBusinessBriefToAdminRoleUsers() {
        Fixture fixture = new Fixture();
        when(fixture.notificationMapper.selectCount(any())).thenReturn(0L);

        int sent = fixture.service.sendTodayBriefs();

        assertThat(sent).isEqualTo(1);
        verify(fixture.notificationService).create(any(NotificationRequest.class), eq(null));
    }

    @Test
    void skipsWhenBriefAlreadySentToday() {
        Fixture fixture = new Fixture();
        when(fixture.notificationMapper.selectCount(any())).thenReturn(1L);

        int sent = fixture.service.sendTodayBriefs();

        assertThat(sent).isZero();
        verify(fixture.notificationService, never()).create(any(), any());
    }

    private static class Fixture {
        final TaskService taskService = mock(TaskService.class);
        final NotificationService notificationService = mock(NotificationService.class);
        final NotificationMapper notificationMapper = mock(NotificationMapper.class);
        final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        final SysUserMapper userMapper = mock(SysUserMapper.class);
        final AgentDailyBriefService service;

        Fixture() {
            SysRole role = new SysRole();
            role.setId(1L);
            role.setRoleCode("SUPER_ADMIN");
            role.setStatus(1);
            role.setDeleted(0);
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(1L);
            userRole.setRoleId(1L);
            SysUser admin = new SysUser();
            admin.setId(1L);
            admin.setUsername("admin");
            admin.setStatus(1);
            admin.setDeleted(0);
            when(roleMapper.selectList(any())).thenReturn(List.of(role));
            when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));
            when(userMapper.selectBatchIds(any())).thenReturn(List.of(admin));
            when(taskService.pageTasks(1, 300, null, null, null, null))
                    .thenReturn(new PageResult<>(1, 300, 2, List.of(
                            task(1L, "TF-1", "逾期重点任务", "TODO", "HIGH", LocalDateTime.now().minusDays(1)),
                            task(2L, "TF-2", "已完成事项", "DONE", "MEDIUM", null)
                    )));
            service = new AgentDailyBriefService(
                    taskService,
                    notificationService,
                    notificationMapper,
                    roleMapper,
                    userRoleMapper,
                    userMapper,
                    true,
                    300,
                    "SUPER_ADMIN",
                    "00:00"
            );
        }

        private TaskListItem task(Long id, String taskNo, String title, String status, String priority, LocalDateTime dueTime) {
            return new TaskListItem(
                    id,
                    1L,
                    "TeamFlow",
                    0L,
                    taskNo,
                    title,
                    title,
                    1L,
                    "系统管理员",
                    List.of(1L),
                    List.of("系统管理员"),
                    1L,
                    "系统管理员",
                    priority,
                    status,
                    null,
                    dueTime,
                    null,
                    null,
                    1,
                    List.of(),
                    LocalDateTime.now().minusDays(2),
                    LocalDateTime.now().minusDays(1)
            );
        }
    }
}

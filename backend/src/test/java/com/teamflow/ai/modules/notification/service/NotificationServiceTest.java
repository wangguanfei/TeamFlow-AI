package com.teamflow.ai.modules.notification.service;

import com.teamflow.ai.modules.notification.dto.NotificationItem;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.entity.Notification;
import com.teamflow.ai.modules.notification.mapper.NotificationMapper;
import com.teamflow.ai.modules.notification.mapper.NotificationReadMapper;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    void createSupportsSystemSenderWithoutUserId() {
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        NotificationReadMapper readMapper = mock(NotificationReadMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        NotificationWebSocketHandler webSocketHandler = mock(NotificationWebSocketHandler.class);
        when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(101L);
            return 1;
        });
        when(readMapper.selectList(any())).thenReturn(List.of());

        NotificationService service = new NotificationService(notificationMapper, readMapper, userMapper, webSocketHandler);

        NotificationItem item = service.create(new NotificationRequest(
                "任务风险提醒",
                "系统主动发现任务风险",
                "TASK_RISK",
                "USER",
                1L,
                "TASK_OVERDUE",
                23L,
                null
        ), null);

        assertThat(item.id()).isEqualTo(101L);
        assertThat(item.senderId()).isNull();
        assertThat(item.senderName()).isEqualTo("系统");
        assertThat(item.bizType()).isEqualTo("TASK_OVERDUE");
        verify(webSocketHandler).push(item);
    }
}

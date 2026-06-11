package com.teamflow.ai.modules.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.notification.dto.NotificationItem;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.entity.Notification;
import com.teamflow.ai.modules.notification.entity.NotificationRead;
import com.teamflow.ai.modules.notification.mapper.NotificationMapper;
import com.teamflow.ai.modules.notification.mapper.NotificationReadMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String TARGET_USER = "USER";
    private static final String TARGET_ALL = "ALL";

    private final NotificationMapper notificationMapper;
    private final NotificationReadMapper readMapper;
    private final SysUserMapper userMapper;
    private final NotificationWebSocketHandler webSocketHandler;

    public NotificationService(
            NotificationMapper notificationMapper,
            NotificationReadMapper readMapper,
            SysUserMapper userMapper,
            NotificationWebSocketHandler webSocketHandler
    ) {
        this.notificationMapper = notificationMapper;
        this.readMapper = readMapper;
        this.userMapper = userMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional
    public NotificationItem create(NotificationRequest request, Long senderId) {
        Notification notification = new Notification();
        notification.setTitle(request.title().trim());
        notification.setContent(request.content());
        notification.setNotifyType(defaultValue(request.notifyType(), "SYSTEM"));
        notification.setTargetType(defaultValue(request.targetType(), TARGET_USER));
        notification.setTargetId(notification.getTargetType().equals(TARGET_USER) ? request.targetId() : null);
        if (notification.getTargetType().equals(TARGET_USER) && notification.getTargetId() == null) {
            throw new BusinessException("用户通知必须指定目标用户");
        }
        notification.setSenderId(senderId);
        notification.setBizType(defaultNullableValue(request.bizType()));
        notification.setBizId(request.bizId());
        notification.setBizTime(request.bizTime());
        notification.setCreatedAt(LocalDateTime.now());
        notification.setDeleted(0);
        notificationMapper.insert(notification);
        NotificationItem item = toItems(List.of(notification), notification.getTargetId()).get(0);
        webSocketHandler.push(item);
        log.info("发送通知 notificationId={} type={} targetType={} targetId={} senderId={} 已 WebSocket 推送",
                notification.getId(), notification.getNotifyType(), notification.getTargetType(),
                notification.getTargetId(), senderId);
        return item;
    }

    public PageResult<NotificationItem> page(long page, long size, String keyword, Boolean unreadOnly, Long currentUserId) {
        LambdaQueryWrapper<Notification> wrapper = visibleWrapper(currentUserId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(Notification::getTitle, keyword)
                        .or()
                        .like(Notification::getContent, keyword)
                        .or()
                        .like(Notification::getNotifyType, keyword))
                .orderByDesc(Notification::getId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            List<Long> readIds = readMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                            .eq(NotificationRead::getUserId, currentUserId))
                    .stream()
                    .map(NotificationRead::getNotificationId)
                    .toList();
            if (!readIds.isEmpty()) {
                wrapper.notIn(Notification::getId, readIds);
            }
        }
        Page<Notification> result = notificationMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        List<NotificationItem> items = toItems(result.getRecords(), currentUserId);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public NotificationItem detail(Long id, Long currentUserId) {
        Notification notification = getVisibleNotification(id, currentUserId);
        return toItems(List.of(notification), currentUserId).get(0);
    }

    public long unreadCount(Long currentUserId) {
        List<Long> visibleIds = notificationMapper.selectList(visibleWrapper(currentUserId))
                .stream()
                .map(Notification::getId)
                .toList();
        if (visibleIds.isEmpty()) {
            return 0;
        }
        long readCount = readMapper.selectCount(new LambdaQueryWrapper<NotificationRead>()
                .eq(NotificationRead::getUserId, currentUserId)
                .in(NotificationRead::getNotificationId, visibleIds));
        return Math.max(0, visibleIds.size() - readCount);
    }

    @Transactional
    public NotificationItem markRead(Long id, Long currentUserId) {
        Notification notification = getVisibleNotification(id, currentUserId);
        ensureRead(notification.getId(), currentUserId);
        return detail(id, currentUserId);
    }

    @Transactional
    public void readAll(Long currentUserId) {
        notificationMapper.selectList(visibleWrapper(currentUserId))
                .forEach(notification -> ensureRead(notification.getId(), currentUserId));
    }

    @Transactional
    public void delete(Long id) {
        notificationMapper.deleteById(id);
        readMapper.delete(new LambdaQueryWrapper<NotificationRead>().eq(NotificationRead::getNotificationId, id));
        log.info("删除通知（含已读记录）notificationId={}", id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> validIds = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        log.info("批量删除通知 notificationIds={}", validIds);
        validIds.forEach(this::delete);
    }

    private void ensureRead(Long notificationId, Long currentUserId) {
        NotificationRead existing = readMapper.selectOne(new LambdaQueryWrapper<NotificationRead>()
                .eq(NotificationRead::getNotificationId, notificationId)
                .eq(NotificationRead::getUserId, currentUserId)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        NotificationRead read = new NotificationRead();
        read.setNotificationId(notificationId);
        read.setUserId(currentUserId);
        read.setReadTime(LocalDateTime.now());
        read.setCreatedAt(LocalDateTime.now());
        readMapper.insert(read);
    }

    private Notification getVisibleNotification(Long id, Long currentUserId) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || notification.getDeleted() == 1 || !isVisible(notification, currentUserId)) {
            throw new BusinessException("通知不存在");
        }
        return notification;
    }

    private LambdaQueryWrapper<Notification> visibleWrapper(Long currentUserId) {
        return new LambdaQueryWrapper<Notification>()
                .eq(Notification::getDeleted, 0)
                .and(query -> query
                        .eq(Notification::getTargetType, TARGET_ALL)
                        .or(inner -> inner
                                .eq(Notification::getTargetType, TARGET_USER)
                                .eq(Notification::getTargetId, currentUserId)));
    }

    private boolean isVisible(Notification notification, Long currentUserId) {
        if (TARGET_ALL.equalsIgnoreCase(notification.getTargetType())) {
            return true;
        }
        return TARGET_USER.equalsIgnoreCase(notification.getTargetType()) && currentUserId.equals(notification.getTargetId());
    }

    private List<NotificationItem> toItems(List<Notification> notifications, Long currentUserId) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        List<Long> senderIds = notifications.stream()
                .map(Notification::getSenderId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, SysUser> senders = senderIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(senderIds).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, NotificationRead> reads = readMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                        .eq(NotificationRead::getUserId, currentUserId)
                        .in(NotificationRead::getNotificationId, notifications.stream().map(Notification::getId).toList()))
                .stream()
                .collect(Collectors.toMap(NotificationRead::getNotificationId, Function.identity(), (left, right) -> left));
        return notifications.stream()
                .map(notification -> {
                    NotificationRead read = reads.get(notification.getId());
                    SysUser sender = senders.get(notification.getSenderId());
                    String senderName = sender == null ? "系统" : (sender.getNickname() == null || sender.getNickname().isBlank()
                            ? sender.getUsername()
                            : sender.getNickname());
                    return new NotificationItem(
                            notification.getId(),
                            notification.getTitle(),
                            notification.getContent(),
                            notification.getNotifyType(),
                            notification.getTargetType(),
                            notification.getTargetId(),
                            notification.getSenderId(),
                            senderName,
                            notification.getBizType(),
                            notification.getBizId(),
                            notification.getBizTime(),
                            read != null,
                            read == null ? null : read.getReadTime(),
                            notification.getCreatedAt()
                    );
                })
                .toList();
    }

    private String defaultValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }

    private String defaultNullableValue(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}

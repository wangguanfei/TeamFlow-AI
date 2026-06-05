package com.teamflow.ai.modules.notification.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.notification.dto.NotificationItem;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.service.NotificationService;
import com.teamflow.ai.modules.ai.dto.IdListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "创建通知")
    @PostMapping
    @PreAuthorize("hasAuthority('notification:create')")
    public ApiResult<NotificationItem> create(@Valid @RequestBody NotificationRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(notificationService.create(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询通知")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResult<PageResult<NotificationItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean unreadOnly,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResult.success(notificationService.page(page, size, keyword, unreadOnly, principal.getUserId()));
    }

    @Operation(summary = "详情查询通知")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResult<NotificationItem> detail(@PathVariable Long id,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(notificationService.detail(id, principal.getUserId()));
    }

    @Operation(summary = "未读通知数量")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResult<Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(notificationService.unreadCount(principal.getUserId()));
    }

    @Operation(summary = "标记通知已读")
    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResult<NotificationItem> markRead(@PathVariable Long id,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(notificationService.markRead(id, principal.getUserId()));
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResult<Void> readAll(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.readAll(principal.getUserId());
        return ApiResult.success();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('notification:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除通知")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('notification:delete')")
    public ApiResult<Void> batchDelete(@Valid @RequestBody IdListRequest request) {
        notificationService.batchDelete(request.ids());
        return ApiResult.success();
    }
}

package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.log.Log;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.ai.dto.AiMessageFeedbackItem;
import com.teamflow.ai.modules.ai.dto.AiMessageFeedbackRequest;
import com.teamflow.ai.modules.ai.dto.AiMessageItem;
import com.teamflow.ai.modules.ai.dto.AiMessageRequest;
import com.teamflow.ai.modules.ai.dto.IdListRequest;
import com.teamflow.ai.modules.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI消息")
@RestController
@RequestMapping("/api/ai-messages")
public class AiMessageController {

    private final AiService aiService;

    public AiMessageController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "创建AI消息")
    @PostMapping
    @PreAuthorize("hasAuthority('ai:message')")
    public ApiResult<AiMessageItem> create(@Valid @RequestBody AiMessageRequest request) {
        return ApiResult.success(aiService.createMessage(request));
    }

    @Operation(summary = "分页查询AI消息")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<PageResult<AiMessageItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(aiService.pageMessages(page, size, sessionId, keyword));
    }

    @Operation(summary = "详情查询AI消息")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<AiMessageItem> detail(@PathVariable Long id) {
        return ApiResult.success(aiService.getMessage(id));
    }

    @Operation(summary = "更新AI消息")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:message')")
    public ApiResult<AiMessageItem> update(@PathVariable Long id, @Valid @RequestBody AiMessageRequest request) {
        return ApiResult.success(aiService.updateMessage(id, request));
    }

    @Operation(summary = "删除AI消息")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:message')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        aiService.deleteMessage(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除AI消息")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('ai:message')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        aiService.batchDeleteMessages(request.ids());
        return ApiResult.success();
    }

    @Log(module = "AI助手", type = "消息反馈")
    @Operation(summary = "提交AI消息反馈")
    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAuthority('ai:chat')")
    public ApiResult<AiMessageFeedbackItem> feedback(@PathVariable Long id,
                                                     @Valid @RequestBody AiMessageFeedbackRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.feedbackMessage(id, request, principal.getUserId()));
    }
}

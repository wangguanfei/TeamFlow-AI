package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.ai.dto.AiSessionItem;
import com.teamflow.ai.modules.ai.dto.AiSessionRequest;
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

@Tag(name = "AI会话")
@RestController
@RequestMapping("/api/ai-sessions")
public class AiSessionController {

    private final AiService aiService;

    public AiSessionController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "创建AI会话")
    @PostMapping
    @PreAuthorize("hasAuthority('ai:session')")
    public ApiResult<AiSessionItem> create(@Valid @RequestBody AiSessionRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.createSession(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询AI会话")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<PageResult<AiSessionItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "true") boolean mine,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResult.success(aiService.pageSessions(page, size, keyword, mine ? principal.getUserId() : null));
    }

    @Operation(summary = "详情查询AI会话")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<AiSessionItem> detail(@PathVariable Long id) {
        return ApiResult.success(aiService.getSession(id));
    }

    @Operation(summary = "更新AI会话")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:session')")
    public ApiResult<AiSessionItem> update(@PathVariable Long id,
                                           @Valid @RequestBody AiSessionRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.updateSession(id, request, principal.getUserId()));
    }

    @Operation(summary = "删除AI会话")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:session')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        aiService.deleteSession(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除AI会话")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('ai:session')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        aiService.batchDeleteSessions(request.ids());
        return ApiResult.success();
    }
}

package com.teamflow.ai.modules.knowledge.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeSpaceItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeSpaceRequest;
import com.teamflow.ai.modules.knowledge.service.KnowledgeService;
import com.teamflow.ai.common.log.Log;
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

@Tag(name = "知识空间")
@RestController
@RequestMapping("/api/knowledge-spaces")
public class KnowledgeSpaceController {

    private final KnowledgeService knowledgeService;

    public KnowledgeSpaceController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Log(module = "知识库", type = "新增空间")
    @Operation(summary = "创建知识空间")
    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:space:create')")
    public ApiResult<KnowledgeSpaceItem> create(@Valid @RequestBody KnowledgeSpaceRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.createSpace(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询知识空间")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<PageResult<KnowledgeSpaceItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(knowledgeService.pageSpaces(page, size, keyword));
    }

    @Operation(summary = "详情查询知识空间")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<KnowledgeSpaceItem> detail(@PathVariable Long id) {
        return ApiResult.success(knowledgeService.getSpace(id));
    }

    @Log(module = "知识库", type = "修改空间")
    @Operation(summary = "更新知识空间")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:space:update')")
    public ApiResult<KnowledgeSpaceItem> update(@PathVariable Long id,
                                                @Valid @RequestBody KnowledgeSpaceRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.updateSpace(id, request, principal.getUserId()));
    }

    @Log(module = "知识库", type = "删除空间")
    @Operation(summary = "删除知识空间")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:space:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteSpace(id);
        return ApiResult.success();
    }
}

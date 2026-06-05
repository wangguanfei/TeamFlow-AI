package com.teamflow.ai.modules.knowledge.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeFavoriteItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeFavoriteRequest;
import com.teamflow.ai.modules.knowledge.service.KnowledgeService;
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

@Tag(name = "知识收藏")
@RestController
@RequestMapping("/api/knowledge-favorites")
public class KnowledgeFavoriteController {

    private final KnowledgeService knowledgeService;

    public KnowledgeFavoriteController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Operation(summary = "收藏知识文档")
    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:favorite')")
    public ApiResult<KnowledgeFavoriteItem> create(@Valid @RequestBody KnowledgeFavoriteRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.createFavorite(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询知识收藏")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<PageResult<KnowledgeFavoriteItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResult.success(knowledgeService.pageFavorites(page, size, principal.getUserId()));
    }

    @Operation(summary = "取消知识收藏")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:favorite')")
    public ApiResult<Void> delete(@PathVariable Long id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        knowledgeService.deleteFavorite(id, principal.getUserId());
        return ApiResult.success();
    }
}

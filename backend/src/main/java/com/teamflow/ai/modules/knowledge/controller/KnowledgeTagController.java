package com.teamflow.ai.modules.knowledge.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeTagItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeTagRequest;
import com.teamflow.ai.modules.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "知识标签")
@RestController
@RequestMapping("/api/knowledge-tags")
public class KnowledgeTagController {

    private final KnowledgeService knowledgeService;

    public KnowledgeTagController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Operation(summary = "创建知识标签")
    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:tag')")
    public ApiResult<KnowledgeTagItem> create(@Valid @RequestBody KnowledgeTagRequest request) {
        return ApiResult.success(knowledgeService.createTag(request.docId(), request.tagName()));
    }

    @Operation(summary = "分页查询知识标签")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<PageResult<KnowledgeTagItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(knowledgeService.pageTags(page, size, docId, keyword));
    }

    @Operation(summary = "删除知识标签")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:tag')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteTag(id);
        return ApiResult.success();
    }
}

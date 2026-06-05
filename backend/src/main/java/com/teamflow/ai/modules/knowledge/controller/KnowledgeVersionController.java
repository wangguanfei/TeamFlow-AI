package com.teamflow.ai.modules.knowledge.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeVersionItem;
import com.teamflow.ai.modules.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "知识版本")
@RestController
@RequestMapping("/api/knowledge-versions")
public class KnowledgeVersionController {

    private final KnowledgeService knowledgeService;

    public KnowledgeVersionController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Operation(summary = "分页查询知识版本")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<PageResult<KnowledgeVersionItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(knowledgeService.pageVersions(page, size, docId, keyword));
    }

    @Operation(summary = "详情查询知识版本")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<KnowledgeVersionItem> detail(@PathVariable Long id) {
        return ApiResult.success(knowledgeService.getVersion(id));
    }
}

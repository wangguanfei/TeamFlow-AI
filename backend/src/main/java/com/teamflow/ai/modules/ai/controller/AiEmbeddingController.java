package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.modules.ai.dto.AiEmbeddingItem;
import com.teamflow.ai.modules.ai.dto.AiEmbeddingRequest;
import com.teamflow.ai.modules.ai.dto.IdListRequest;
import com.teamflow.ai.modules.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI向量")
@RestController
@RequestMapping("/api/ai-embeddings")
public class AiEmbeddingController {

    private final AiService aiService;

    public AiEmbeddingController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "创建AI向量")
    @PostMapping
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<AiEmbeddingItem> create(@RequestBody AiEmbeddingRequest request) {
        return ApiResult.success(aiService.createEmbedding(request));
    }

    @Operation(summary = "分页查询AI向量")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<PageResult<AiEmbeddingItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(aiService.pageEmbeddings(page, size, docId, keyword));
    }

    @Operation(summary = "详情查询AI向量")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<AiEmbeddingItem> detail(@PathVariable Long id) {
        return ApiResult.success(aiService.getEmbedding(id));
    }

    @Operation(summary = "更新AI向量")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<AiEmbeddingItem> update(@PathVariable Long id, @RequestBody AiEmbeddingRequest request) {
        return ApiResult.success(aiService.updateEmbedding(id, request));
    }

    @Operation(summary = "删除AI向量")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        aiService.deleteEmbedding(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除AI向量")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        aiService.batchDeleteEmbeddings(request.ids());
        return ApiResult.success();
    }
}

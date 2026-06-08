package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.modules.ai.dto.RagRebuildResponse;
import com.teamflow.ai.modules.ai.dto.RagStatus;
import com.teamflow.ai.modules.ai.service.AiKnowledgeIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "RAG运维")
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final AiKnowledgeIndexService knowledgeIndexService;

    public RagController(AiKnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @Operation(summary = "RAG状态")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<RagStatus> status() {
        return ApiResult.success(knowledgeIndexService.ragStatus());
    }

    @Operation(summary = "重建单文档RAG索引")
    @PostMapping("/index/documents/{docId}/rebuild")
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<RagRebuildResponse> rebuildDocument(@PathVariable Long docId) {
        return ApiResult.success(new RagRebuildResponse(knowledgeIndexService.enqueueRebuild(docId)));
    }

    @Operation(summary = "批量重建RAG索引")
    @PostMapping("/index/rebuild")
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<RagRebuildResponse> rebuildSpace(@RequestParam(required = false) Long spaceId) {
        return ApiResult.success(new RagRebuildResponse(knowledgeIndexService.enqueueRebuildSpace(spaceId)));
    }
}

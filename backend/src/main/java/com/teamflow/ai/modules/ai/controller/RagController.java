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

/**
 * RAG 运维控制器（/api/rag/**）。
 * <p>
 * 提供 RAG 系统的健康状态查询和索引重建接口，供运维人员和开发者使用。
 * <ul>
 *   <li>GET /api/rag/status：查看 RAG 各组件（Qdrant/embedding-service/内存门槛）状态</li>
 *   <li>POST /api/rag/index/documents/{docId}/rebuild：重建单文档向量索引</li>
 *   <li>POST /api/rag/index/rebuild[?spaceId=]：重建整个空间或全量文档（慎用，耗时较长）</li>
 * </ul>
 * 所有重建操作均为异步入队，接口立即返回队列任务数，实际执行由后台 worker 完成。
 */
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
        // 这里只入队，真正切块/embedding/Qdrant 写入由后台 worker 异步完成。
        return ApiResult.success(new RagRebuildResponse(knowledgeIndexService.enqueueRebuild(docId)));
    }

    @Operation(summary = "批量重建RAG索引")
    @PostMapping("/index/rebuild")
    @PreAuthorize("hasAuthority('ai:embedding')")
    public ApiResult<RagRebuildResponse> rebuildSpace(@RequestParam(required = false) Long spaceId) {
        // spaceId 为空时重建所有已发布文档，适合模型或向量维度升级后的全量重建。
        return ApiResult.success(new RagRebuildResponse(knowledgeIndexService.enqueueRebuildSpace(spaceId)));
    }
}

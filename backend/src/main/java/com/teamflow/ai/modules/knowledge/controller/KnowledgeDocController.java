package com.teamflow.ai.modules.knowledge.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocRequest;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocTreeNode;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeImportResult;
import com.teamflow.ai.modules.knowledge.dto.KnowledgePublishRequest;
import com.teamflow.ai.modules.knowledge.service.KnowledgeImportService;
import com.teamflow.ai.modules.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Tag(name = "知识文档")
@RestController
@RequestMapping("/api/knowledge-docs")
public class KnowledgeDocController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeImportService knowledgeImportService;

    public KnowledgeDocController(KnowledgeService knowledgeService, KnowledgeImportService knowledgeImportService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeImportService = knowledgeImportService;
    }

    @Operation(summary = "创建知识文档")
    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:doc:create')")
    public ApiResult<KnowledgeDocItem> create(@Valid @RequestBody KnowledgeDocRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.createDoc(request, principal.getUserId()));
    }

    @Operation(summary = "上传文件并导入知识库文档")
    @PostMapping(value = "/import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('knowledge:doc:create') and hasAuthority('file:upload')")
    public ApiResult<KnowledgeImportResult> importFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long spaceId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "true") boolean autoPublish,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResult.success(knowledgeImportService.importFile(
                file,
                spaceId,
                parentId,
                title,
                parseTags(tags),
                autoPublish,
                principal.getUserId()
        ));
    }

    @Operation(summary = "分页查询知识文档")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<PageResult<KnowledgeDocItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResult.success(knowledgeService.pageDocs(page, size, spaceId, keyword, principal.getUserId()));
    }

    @Operation(summary = "查询知识文档树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<List<KnowledgeDocTreeNode>> tree(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(knowledgeService.tree(spaceId, keyword));
    }

    @Operation(summary = "详情查询知识文档")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:view')")
    public ApiResult<KnowledgeDocItem> detail(@PathVariable Long id,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.getDoc(id, principal.getUserId()));
    }

    @Operation(summary = "更新知识文档")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:doc:update')")
    public ApiResult<KnowledgeDocItem> update(@PathVariable Long id,
                                              @Valid @RequestBody KnowledgeDocRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.updateDoc(id, request, principal.getUserId()));
    }

    @Operation(summary = "发布知识文档")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('knowledge:doc:publish')")
    public ApiResult<KnowledgeDocItem> publish(@PathVariable Long id,
                                               @RequestBody(required = false) KnowledgePublishRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.publish(id, request, principal.getUserId()));
    }

    @Operation(summary = "回滚知识文档历史版本")
    @PostMapping("/{id}/restore/{versionId}")
    @PreAuthorize("hasAuthority('knowledge:doc:restore')")
    public ApiResult<KnowledgeDocItem> restore(@PathVariable Long id,
                                               @PathVariable Long versionId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(knowledgeService.restore(id, versionId, principal.getUserId()));
    }

    @Operation(summary = "删除知识文档")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:doc:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return ApiResult.success();
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }
}

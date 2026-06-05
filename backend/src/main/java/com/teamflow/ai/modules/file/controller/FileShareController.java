package com.teamflow.ai.modules.file.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.file.dto.FileShareItem;
import com.teamflow.ai.modules.file.dto.FileShareRequest;
import com.teamflow.ai.modules.file.dto.IdListRequest;
import com.teamflow.ai.modules.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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

@Tag(name = "文件分享")
@RestController
@RequestMapping("/api/file-shares")
public class FileShareController {

    private final FileService fileService;

    public FileShareController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "创建文件分享")
    @PostMapping
    @PreAuthorize("hasAuthority('file:share')")
    public ApiResult<FileShareItem> create(@Valid @RequestBody FileShareRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(fileService.createShare(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询文件分享")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('file:view')")
    public ApiResult<PageResult<FileShareItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(fileService.pageShares(page, size, keyword));
    }

    @Operation(summary = "详情查询文件分享")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('file:view')")
    public ApiResult<FileShareItem> detail(@PathVariable Long id) {
        return ApiResult.success(fileService.getShare(id));
    }

    @Operation(summary = "更新文件分享")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('file:share')")
    public ApiResult<FileShareItem> update(@PathVariable Long id,
                                           @Valid @RequestBody FileShareRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(fileService.updateShare(id, request, principal.getUserId()));
    }

    @Operation(summary = "删除文件分享")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('file:share')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        fileService.deleteShare(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除文件分享")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('file:share')")
    public ApiResult<Void> batchDelete(@RequestBody IdListRequest request) {
        fileService.batchDeleteShares(request.ids());
        return ApiResult.success();
    }

    @Operation(summary = "公开访问-分享详情")
    @GetMapping("/access/{shareCode}")
    public ApiResult<FileShareItem> access(@PathVariable String shareCode) {
        return ApiResult.success(fileService.getShareByCode(shareCode));
    }

    @Operation(summary = "公开访问-分享预览")
    @GetMapping("/access/{shareCode}/preview")
    public ResponseEntity<Resource> accessPreview(@PathVariable String shareCode) {
        return FileResponseSupport.preview(fileService.loadShareContent(shareCode));
    }

    @Operation(summary = "公开访问-分享下载")
    @GetMapping("/access/{shareCode}/download")
    public ResponseEntity<Resource> accessDownload(@PathVariable String shareCode) {
        return FileResponseSupport.download(fileService.loadShareContent(shareCode));
    }
}

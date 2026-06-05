package com.teamflow.ai.modules.file.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.file.dto.FileContent;
import com.teamflow.ai.modules.file.dto.FileItem;
import com.teamflow.ai.modules.file.dto.FileUpdateRequest;
import com.teamflow.ai.modules.file.dto.IdListRequest;
import com.teamflow.ai.modules.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Tag(name = "文件")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final Set<String> INLINE_PREVIEW_CONTENT_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "text/csv",
            "text/markdown",
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/avif"
    );

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "创建文件元数据")
    @PostMapping
    @PreAuthorize("hasAuthority('file:upload')")
    public ApiResult<FileItem> create(@RequestBody FileUpdateRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(fileService.createMetadata(request, principal.getUserId()));
    }

    @Operation(summary = "文件上传")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('file:upload')")
    public ApiResult<FileItem> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResult.success(fileService.upload(file, bizType, bizId, principal.getUserId()));
    }

    @Operation(summary = "分页查询文件")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('file:view')")
    public ApiResult<PageResult<FileItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId
    ) {
        return ApiResult.success(fileService.pageFiles(page, size, keyword, bizType, bizId));
    }

    @Operation(summary = "详情查询文件")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('file:view')")
    public ApiResult<FileItem> detail(@PathVariable Long id) {
        return ApiResult.success(fileService.getFile(id));
    }

    @Operation(summary = "更新文件")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('file:update')")
    public ApiResult<FileItem> update(@PathVariable Long id, @RequestBody FileUpdateRequest request) {
        return ApiResult.success(fileService.updateFile(id, request));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('file:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        fileService.deleteFile(id);
        return ApiResult.success();
    }

    @Operation(summary = "批量删除文件")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('file:delete')")
    public ApiResult<Void> batchDelete(@Valid @RequestBody IdListRequest request) {
        fileService.batchDeleteFiles(request.ids());
        return ApiResult.success();
    }

    @Operation(summary = "文件下载")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('file:view')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileContent content = fileService.loadContent(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
                .contentType(parseMediaType(content.contentType()))
                .contentLength(content.contentLength())
                .body(content.resource());
    }

    @Operation(summary = "文件预览")
    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('file:view')")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        FileContent content = fileService.loadContent(id);
        MediaType mediaType = parseMediaType(content.contentType());
        boolean inlinePreview = isInlinePreviewSafe(mediaType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, (inlinePreview ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(content.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
                .contentType(inlinePreview ? mediaType : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.contentLength())
                .body(content.resource());
    }

    private boolean isInlinePreviewSafe(MediaType mediaType) {
        String contentType = (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase(Locale.ROOT);
        return INLINE_PREVIEW_CONTENT_TYPES.contains(contentType);
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

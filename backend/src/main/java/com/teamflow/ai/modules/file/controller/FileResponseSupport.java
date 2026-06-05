package com.teamflow.ai.modules.file.controller;

import com.teamflow.ai.modules.file.dto.FileContent;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 文件下载/预览响应的统一构造逻辑，供受保护接口与公开分享接口共用。
 */
final class FileResponseSupport {

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

    private FileResponseSupport() {
    }

    static ResponseEntity<Resource> download(FileContent content) {
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

    static ResponseEntity<Resource> preview(FileContent content) {
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

    private static boolean isInlinePreviewSafe(MediaType mediaType) {
        String contentType = (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase(Locale.ROOT);
        return INLINE_PREVIEW_CONTENT_TYPES.contains(contentType);
    }

    private static MediaType parseMediaType(String contentType) {
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

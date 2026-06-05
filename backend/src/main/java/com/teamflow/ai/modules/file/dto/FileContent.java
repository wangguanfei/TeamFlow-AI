package com.teamflow.ai.modules.file.dto;

import org.springframework.core.io.Resource;

public record FileContent(
        Resource resource,
        String fileName,
        String contentType,
        long contentLength
) {
}

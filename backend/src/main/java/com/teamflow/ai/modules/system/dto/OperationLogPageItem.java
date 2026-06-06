package com.teamflow.ai.modules.system.dto;

import java.time.LocalDateTime;

public record OperationLogPageItem(
        Long id,
        Long userId,
        String username,
        String moduleName,
        String operationType,
        String requestMethod,
        String requestUri,
        String requestParams,
        Integer responseStatus,
        String errorMessage,
        Long costMs,
        String clientIp,
        LocalDateTime createdAt
) {}

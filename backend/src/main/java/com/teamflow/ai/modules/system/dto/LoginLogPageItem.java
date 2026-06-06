package com.teamflow.ai.modules.system.dto;

import java.time.LocalDateTime;

public record LoginLogPageItem(
        Long id,
        Long userId,
        String username,
        String loginIp,
        String loginLocation,
        String browser,
        String os,
        String userAgent,
        Integer status,
        String message,
        LocalDateTime createdAt
) {}

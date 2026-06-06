package com.teamflow.ai.modules.system.dto;

import jakarta.validation.constraints.Min;

public record LoginLogCleanRequest(
        @Min(1) int beforeDays
) {}

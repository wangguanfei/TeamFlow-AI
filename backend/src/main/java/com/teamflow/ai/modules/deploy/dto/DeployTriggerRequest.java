package com.teamflow.ai.modules.deploy.dto;

import jakarta.validation.constraints.Pattern;

public record DeployTriggerRequest(
        @Pattern(regexp = "all|backend|frontend", message = "target 只能是 all/backend/frontend")
        String target,
        boolean skipPull
) {
    public DeployTriggerRequest {
        if (target == null || target.isBlank()) target = "all";
    }
}

package com.teamflow.ai.modules.deploy.dto;

import jakarta.validation.constraints.Pattern;

public record DeployTriggerRequest(
        @Pattern(regexp = "all|backend|frontend|rag", message = "target 只能是 all/backend/frontend/rag")
        String target,
        boolean skipPull
) {
    public DeployTriggerRequest {
        if (target == null || target.isBlank()) target = "all";
    }
}

package com.teamflow.ai.modules.user.dto;

public record ProfileOverviewResponse(
        Long responsibleTaskCount,
        Long executingTaskCount,
        Long ownedProjectCount,
        Long knowledgeDocCount,
        Long fileCount,
        Long aiSessionCount,
        Long roleCount,
        Long permissionCount
) {
}

package com.teamflow.ai.modules.deploy.dto;

import com.teamflow.ai.modules.deploy.entity.DeployRecord;

import java.time.LocalDateTime;

public record DeployRecordItem(
        Long id,
        String target,
        String status,
        String triggerUsername,
        Integer exitCode,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long costMs
) {
    public static DeployRecordItem from(DeployRecord r) {
        return new DeployRecordItem(
                r.getId(), r.getTarget(), r.getStatus(),
                r.getTriggerUsername(), r.getExitCode(),
                r.getStartedAt(), r.getFinishedAt(), r.getCostMs()
        );
    }
}

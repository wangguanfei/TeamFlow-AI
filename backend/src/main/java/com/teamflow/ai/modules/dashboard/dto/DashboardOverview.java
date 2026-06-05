package com.teamflow.ai.modules.dashboard.dto;

public record DashboardOverview(
        long userCount,
        long projectCount,
        long taskCount,
        long doneTaskCount,
        long knowledgeDocCount,
        long aiMessageCount
) {
}

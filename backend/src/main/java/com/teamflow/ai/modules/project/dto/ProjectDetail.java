package com.teamflow.ai.modules.project.dto;

import java.util.List;

public record ProjectDetail(
        ProjectListItem project,
        List<ProjectMemberItem> members,
        List<ProjectTagItem> tags
) {
}

package com.teamflow.ai.modules.task.dto;

import java.util.List;

public record TaskDetail(
        TaskListItem task,
        List<TaskExecutorItem> executors,
        List<TaskCommentItem> comments,
        List<TaskWorklogItem> worklogs,
        List<TaskAttachmentItem> attachments,
        List<TaskTagItem> tags
) {
}

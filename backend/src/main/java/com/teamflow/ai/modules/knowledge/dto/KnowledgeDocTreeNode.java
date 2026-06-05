package com.teamflow.ai.modules.knowledge.dto;

import java.util.List;

public record KnowledgeDocTreeNode(
        Long id,
        Long parentId,
        String title,
        String docStatus,
        Integer versionNo,
        List<KnowledgeTagItem> tags,
        List<KnowledgeDocTreeNode> children
) {
}

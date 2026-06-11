package com.teamflow.ai.modules.ai.rag;

import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record RagSearchScope(Long userId, boolean unrestricted, Set<Long> teamIds) {

    public static RagSearchScope anonymous() {
        return new RagSearchScope(null, false, Set.of());
    }

    public static RagSearchScope unrestricted(Long userId) {
        return new RagSearchScope(userId, true, Set.of());
    }

    public RagSearchScope {
        teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
    }

    public boolean canAccess(KnowledgeSpace space) {
        if (space == null || space.getDeleted() != null && space.getDeleted() == 1) {
            return false;
        }
        if (unrestricted) {
            return true;
        }
        String visibility = normalize(space.getVisibility());
        if ("PUBLIC".equals(visibility)) {
            return true;
        }
        if (userId != null && Objects.equals(space.getOwnerId(), userId)) {
            return true;
        }
        return "TEAM".equals(visibility)
                && space.getTeamId() != null
                && teamIds.contains(space.getTeamId());
    }

    public String cacheKey() {
        if (unrestricted) {
            return "scope:all:" + (userId == null ? "system" : userId);
        }
        if (userId == null) {
            return "scope:public";
        }
        String teams = teamIds.stream()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "scope:user:" + userId + ":teams:" + teams;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "TEAM" : value.trim().toUpperCase();
    }
}

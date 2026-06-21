package com.teamflow.ai.modules.ai.rag;

import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索可见范围。
 *
 * <p>搜索缓存和空间过滤都依赖这个对象：超级管理员 unrestricted，可看所有空间；
 * 普通用户能看 PUBLIC、自己拥有的空间、以及所属团队的 TEAM 空间；匿名用户只能看 PUBLIC。</p>
 */
public record RagSearchScope(Long userId, boolean unrestricted, Set<Long> teamIds) {

    /** 匿名用户（未登录），只能看 PUBLIC 空间的知识内容。 */
    public static RagSearchScope anonymous() {
        return new RagSearchScope(null, false, Set.of());
    }

    /** 超级管理员，可访问所有空间，不需要空间 ID 过滤。 */
    public static RagSearchScope unrestricted(Long userId) {
        return new RagSearchScope(userId, true, Set.of());
    }

    public RagSearchScope {
        teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
    }

    /**
     * 判断当前用户是否有权限访问指定知识空间。
     * 可见性规则：unrestricted（超管）> PUBLIC 空间 > 本人拥有 > TEAM 空间且在同一团队。
     */
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
        // 缓存 key 必须稳定排序 teamIds，避免同一用户因集合遍历顺序不同产生多份缓存。
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

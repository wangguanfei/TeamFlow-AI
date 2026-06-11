package com.teamflow.ai.modules.ai.rag;

import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagSearchScopeTest {

    @Test
    void publicSpaceVisibleToAnonymousUser() {
        RagSearchScope scope = RagSearchScope.anonymous();

        assertThat(scope.canAccess(space(1L, "PUBLIC", 99L))).isTrue();
        assertThat(scope.canAccess(space(1L, "TEAM", 99L))).isFalse();
        assertThat(scope.canAccess(space(1L, "PRIVATE", 99L))).isFalse();
    }

    @Test
    void teamSpaceVisibleOnlyToTeamMemberOrOwner() {
        RagSearchScope scope = new RagSearchScope(7L, false, Set.of(10L));

        assertThat(scope.canAccess(space(10L, "TEAM", 99L))).isTrue();
        assertThat(scope.canAccess(space(11L, "TEAM", 99L))).isFalse();
        assertThat(scope.canAccess(space(11L, "PRIVATE", 7L))).isTrue();
    }

    @Test
    void cacheKeyIncludesUserAndTeams() {
        RagSearchScope left = new RagSearchScope(7L, false, Set.of(10L, 11L));
        RagSearchScope right = new RagSearchScope(8L, false, Set.of(10L, 11L));

        assertThat(left.cacheKey()).contains("user:7").contains("10,11");
        assertThat(left.cacheKey()).isNotEqualTo(right.cacheKey());
    }

    @Test
    void unrestrictedScopeCanAccessEverySpace() {
        RagSearchScope scope = RagSearchScope.unrestricted(1L);

        assertThat(scope.canAccess(space(10L, "PRIVATE", 99L))).isTrue();
    }

    private KnowledgeSpace space(Long teamId, String visibility, Long ownerId) {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(teamId + 100);
        space.setTeamId(teamId);
        space.setVisibility(visibility);
        space.setOwnerId(ownerId);
        space.setDeleted(0);
        return space;
    }
}

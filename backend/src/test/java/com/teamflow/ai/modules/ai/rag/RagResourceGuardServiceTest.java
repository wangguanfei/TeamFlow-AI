package com.teamflow.ai.modules.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 本地 Embedding 内存守卫测试。
 *
 * <p>注意：{@code memAvailableMb()} 依赖 Linux {@code /proc/meminfo}，在 macOS/Windows 上读不到，
 * 此时按「容错放行」（返回 -1 即视为不限制），这些用例覆盖该 fail-open 语义与开关分支。
 */
class RagResourceGuardServiceTest {

    @Test
    @DisplayName("关闭本地 Embedding 时直接放行，不读取内存")
    void localEmbeddingDisabled_alwaysAllowed() {
        RagProperties properties = new RagProperties();
        properties.setLocalEmbedding(false);
        properties.setMinAvailableMemoryMb(1_000_000); // 即便门槛极高也应放行
        RagResourceGuardService guard = new RagResourceGuardService(properties);

        assertThat(guard.localEmbeddingAllowed()).isTrue();
    }

    @Test
    @DisplayName("读不到 /proc/meminfo 时容错放行（fail-open），不阻断索引")
    void memInfoUnavailable_failOpen() {
        RagProperties properties = new RagProperties();
        properties.setLocalEmbedding(true);
        properties.setMinAvailableMemoryMb(250);
        RagResourceGuardService guard = new RagResourceGuardService(properties);

        long available = guard.memAvailableMb();
        // 非 Linux 环境读不到，返回 -1；Linux 环境返回真实可用内存
        if (available < 0) {
            assertThat(guard.localEmbeddingAllowed())
                    .as("读不到内存信息应放行，避免误杀索引")
                    .isTrue();
        } else {
            assertThat(guard.localEmbeddingAllowed())
                    .isEqualTo(available >= properties.getMinAvailableMemoryMb());
        }
    }

    @Test
    @DisplayName("memAvailableMb 在任何平台都不抛异常")
    void memAvailableMb_neverThrows() {
        RagResourceGuardService guard = new RagResourceGuardService(new RagProperties());
        assertThat(guard.memAvailableMb()).isGreaterThanOrEqualTo(-1);
    }
}

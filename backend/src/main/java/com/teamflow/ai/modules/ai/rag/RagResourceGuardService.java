package com.teamflow.ai.modules.ai.rag;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

/**
 * RAG 本地资源保护。
 *
 * <p>当前部署目标包含 2C/2G 的小规格机器，本地 embedding 推理可能和 MySQL、Redis、后端进程抢内存。
 * 这里在索引和检索前读取 /proc/meminfo，内存低于阈值时阻止本地 embedding 调用，
 * 让系统退回关键词检索，而不是触发 OOM。</p>
 */
@Service
public class RagResourceGuardService {

    private final RagProperties properties;

    public RagResourceGuardService(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取当前系统可用内存（MB）。
     * <ul>
     *   <li>Linux/容器：解析 /proc/meminfo MemAvailable 字段</li>
     *   <li>macOS/无该文件：返回 -1，调用方不拦截（避免本地开发完全不可用）</li>
     * </ul>
     */
    public long memAvailableMb() {
        try (var lines = Files.lines(Path.of("/proc/meminfo"))) {
            // Linux 容器/宿主机都能提供 MemAvailable；macOS 本地开发没有该文件时返回 -1。
            OptionalLong kb = lines
                    .filter(line -> line.startsWith("MemAvailable:"))
                    .map(line -> line.replaceAll("[^0-9]", ""))
                    .filter(value -> !value.isBlank())
                    .mapToLong(Long::parseLong)
                    .findFirst();
            return kb.isPresent() ? kb.getAsLong() / 1024 : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * 判断当前是否允许发起本地 embedding 推理。
     * localEmbedding=false（云端 embedding 模式）时直接放行；
     * 可用内存 < minAvailableMemoryMb 时拦截，让调用方降级到关键词检索。
     */
    public boolean localEmbeddingAllowed() {
        if (!properties.isLocalEmbedding()) {
            return true;
        }
        long available = memAvailableMb();
        // 读取不到内存信息时不强行关闭 embedding，避免非 Linux 开发环境完全不可用。
        return available < 0 || available >= properties.getMinAvailableMemoryMb();
    }
}

package com.teamflow.ai.modules.ai.rag;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

@Service
public class RagResourceGuardService {

    private final RagProperties properties;

    public RagResourceGuardService(RagProperties properties) {
        this.properties = properties;
    }

    public long memAvailableMb() {
        try (var lines = Files.lines(Path.of("/proc/meminfo"))) {
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

    public boolean localEmbeddingAllowed() {
        if (!properties.isLocalEmbedding()) {
            return true;
        }
        long available = memAvailableMb();
        return available < 0 || available >= properties.getMinAvailableMemoryMb();
    }
}

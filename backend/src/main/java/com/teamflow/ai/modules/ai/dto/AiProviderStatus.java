package com.teamflow.ai.modules.ai.dto;

public record AiProviderStatus(
        String provider,
        String model,
        boolean configured,
        boolean mock
) {
}

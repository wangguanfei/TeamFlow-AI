package com.teamflow.ai.modules.ai.provider;

import com.teamflow.ai.modules.ai.dto.AiReferenceItem;

import java.util.List;
import java.util.function.Consumer;

public interface AiProvider {

    AiAnswer chat(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references, String model);

    default AiAnswer chatStream(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references,
                                String model, Consumer<String> tokenConsumer) {
        AiAnswer answer = chat(messages, mode, references, model);
        tokenConsumer.accept(answer.content());
        return answer;
    }

    record AiPromptMessage(String role, String content) {
    }

    record AiAnswer(String content, int tokens, String modelName, boolean mock) {
    }
}

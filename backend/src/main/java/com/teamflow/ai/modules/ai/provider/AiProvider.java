package com.teamflow.ai.modules.ai.provider;

import com.teamflow.ai.modules.ai.dto.AiReferenceItem;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface AiProvider {

    AiAnswer chat(List<AiPromptMessage> messages, String mode, List<AiReferenceItem> references, String model);

    default AiAgentAnswer chatWithTools(List<AiPromptMessage> messages, List<AiToolDefinition> tools, String model) {
        AiAnswer answer = chat(messages, "AGENT", List.of(), model);
        return new AiAgentAnswer(answer.content(), List.of(), answer.tokens(), answer.modelName(), answer.mock());
    }

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

    record AiToolDefinition(String name, String description, Map<String, Object> parameters) {
    }

    record AiToolCall(String id, String name, Map<String, Object> arguments) {
    }

    record AiAgentAnswer(String content, List<AiToolCall> toolCalls, int tokens, String modelName, boolean mock) {
    }
}

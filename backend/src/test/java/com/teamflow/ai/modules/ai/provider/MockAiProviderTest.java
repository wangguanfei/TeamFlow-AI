package com.teamflow.ai.modules.ai.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider();

    @Test
    void singleTaskProgressUsesTaskProgressDetailBeforeProjectSummary() {
        AiProvider.AiAgentAnswer answer = provider.chatWithTools(
                List.of(new AiProvider.AiPromptMessage("USER", "TF-4这个任务进展如何了")),
                List.of(tool("query_project_summary"), tool("task_progress_detail"))
        );

        assertThat(answer.toolCalls()).hasSize(1);
        AiProvider.AiToolCall toolCall = answer.toolCalls().get(0);
        assertThat(toolCall.name()).isEqualTo("task_progress_detail");
        assertThat(toolCall.arguments()).containsEntry("taskNo", "TF-4");
    }

    @Test
    void projectProgressStillUsesProjectSummary() {
        AiProvider.AiAgentAnswer answer = provider.chatWithTools(
                List.of(new AiProvider.AiPromptMessage("USER", "帮我汇总一下项目进展")),
                List.of(tool("query_project_summary"), tool("task_progress_detail"))
        );

        assertThat(answer.toolCalls()).hasSize(1);
        assertThat(answer.toolCalls().get(0).name()).isEqualTo("query_project_summary");
    }

    private AiProvider.AiToolDefinition tool(String name) {
        return new AiProvider.AiToolDefinition(name, name, Map.of("type", "object"));
    }
}

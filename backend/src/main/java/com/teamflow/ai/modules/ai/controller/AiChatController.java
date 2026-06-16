package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.log.Log;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.agent.dto.AgentActionResult;
import com.teamflow.ai.modules.agent.dto.AgentCancelRequest;
import com.teamflow.ai.modules.agent.dto.AgentChatRequest;
import com.teamflow.ai.modules.agent.dto.AgentConfirmRequest;
import com.teamflow.ai.modules.agent.dto.AgentToolItem;
import com.teamflow.ai.modules.agent.service.AgentOrchestrator;
import com.teamflow.ai.modules.agent.service.AgentToolRegistry;
import com.teamflow.ai.modules.ai.dto.AiChatRequest;
import com.teamflow.ai.modules.ai.dto.AiChatResponse;
import com.teamflow.ai.modules.ai.dto.AiProviderStatus;
import com.teamflow.ai.modules.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "AI会话")
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiService aiService;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentToolRegistry agentToolRegistry;

    public AiChatController(AiService aiService,
                            AgentOrchestrator agentOrchestrator,
                            AgentToolRegistry agentToolRegistry) {
        this.aiService = aiService;
        this.agentOrchestrator = agentOrchestrator;
        this.agentToolRegistry = agentToolRegistry;
    }

    @Operation(summary = "AI提供商状态")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ai:view')")
    public ApiResult<AiProviderStatus> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.providerStatus(principal.getUserId()));
    }

    @Operation(summary = "AI流式聊天（SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:chat')")
    public SseEmitter chat(@Valid @RequestBody AiChatRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return aiService.chatStream(request, principal.getUserId());
    }

    @Operation(summary = "AI企业助理流式对话")
    @PostMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:agent')")
    public SseEmitter agentChat(@Valid @RequestBody AgentChatRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return agentOrchestrator.chat(request, principal);
    }

    @Log(module = "AI企业助理", type = "确认执行", source = "AI_AGENT")
    @Operation(summary = "确认并执行AI待办动作")
    @PostMapping("/agent/confirm")
    @PreAuthorize("hasAuthority('ai:agent')")
    public ApiResult<AgentActionResult> confirmAgentAction(@Valid @RequestBody AgentConfirmRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(agentOrchestrator.confirm(request.confirmToken(), principal));
    }

    @Operation(summary = "取消AI待办动作")
    @PostMapping("/agent/cancel")
    @PreAuthorize("hasAuthority('ai:agent')")
    public ApiResult<AgentActionResult> cancelAgentAction(@Valid @RequestBody AgentCancelRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(agentOrchestrator.cancel(request.confirmToken(), principal));
    }

    @Operation(summary = "查询当前可用AI工具")
    @GetMapping("/agent/tools")
    @PreAuthorize("hasAuthority('ai:agent')")
    public ApiResult<List<AgentToolItem>> agentTools() {
        return ApiResult.success(agentToolRegistry.list());
    }

    @Operation(summary = "知识库问答RAG")
    @PostMapping("/knowledge/ask")
    @PreAuthorize("hasAuthority('ai:chat')")
    public ApiResult<AiChatResponse> knowledgeAsk(@Valid @RequestBody AiChatRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.knowledgeAsk(request, principal.getUserId()));
    }

    @Operation(summary = "文档总结")
    @PostMapping("/doc/summary")
    @PreAuthorize("hasAuthority('ai:chat')")
    public ApiResult<AiChatResponse> documentSummary(@Valid @RequestBody AiChatRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.documentSummary(request, principal.getUserId()));
    }

    @Operation(summary = "代码生成")
    @PostMapping("/code/generate")
    @PreAuthorize("hasAuthority('ai:chat')")
    public ApiResult<AiChatResponse> codeGenerate(@Valid @RequestBody AiChatRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(aiService.codeGenerate(request, principal.getUserId()));
    }
}

package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.log.Log;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.agent.dto.AgentActionPageItem;
import com.teamflow.ai.modules.agent.dto.AgentActionResult;
import com.teamflow.ai.modules.agent.dto.AgentCancelRequest;
import com.teamflow.ai.modules.agent.dto.AgentChatRequest;
import com.teamflow.ai.modules.agent.dto.AgentConfirmRequest;
import com.teamflow.ai.modules.agent.dto.AgentToolItem;
import com.teamflow.ai.modules.agent.service.AgentActionService;
import com.teamflow.ai.modules.agent.service.AgentOrchestrator;
import com.teamflow.ai.modules.agent.service.AgentToolRegistry;
import com.teamflow.ai.modules.ai.dto.AiChatRequest;
import com.teamflow.ai.modules.ai.dto.AiChatResponse;
import com.teamflow.ai.modules.ai.dto.AiProviderStatus;
import com.teamflow.ai.modules.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 对话控制器，负责所有与 AI 推理相关的 HTTP 接口（/api/ai/**）。
 * <p>
 * 包含三类能力：
 * <ul>
 *   <li>普通 SSE 流式聊天（POST /api/ai/chat/stream）：逐 token 推流，前端 EventSource 接收</li>
 *   <li>Agent 智能助理（POST /api/ai/agent/chat）：支持工具调用，写工具须二次 confirm 确认</li>
 *   <li>快捷专项接口（知识问答、文档总结、代码生成）：阻塞式，适合后台小批量调用</li>
 * </ul>
 * 依赖 {@link AiService} 处理普通聊天，{@link AgentOrchestrator} 处理 Agent 编排。
 */
@Tag(name = "AI会话")
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiService aiService;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentToolRegistry agentToolRegistry;
    private final AgentActionService agentActionService;

    public AiChatController(AiService aiService,
                            AgentOrchestrator agentOrchestrator,
                            AgentToolRegistry agentToolRegistry,
                            AgentActionService agentActionService) {
        this.aiService = aiService;
        this.agentOrchestrator = agentOrchestrator;
        this.agentToolRegistry = agentToolRegistry;
        this.agentActionService = agentActionService;
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
        // 普通聊天统一走 SSE，前端收到 token/done/error 三类事件。
        return aiService.chatStream(request, principal.getUserId());
    }

    @Operation(summary = "AI企业助理流式对话")
    @PostMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:agent')")
    public SseEmitter agentChat(@Valid @RequestBody AgentChatRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        // Agent SSE 事件比普通聊天多工具调用、工具结果和待确认动作。
        return agentOrchestrator.chat(request, principal);
    }

    @Log(module = "AI企业助理", type = "确认执行", source = "AI_AGENT")
    @Operation(summary = "确认并执行AI待办动作")
    @PostMapping("/agent/confirm")
    @PreAuthorize("hasAuthority('ai:agent')")
    public ApiResult<AgentActionResult> confirmAgentAction(@Valid @RequestBody AgentConfirmRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        // 写工具必须通过 confirmToken 二次确认后才执行，不能直接在 /agent/chat 中落库。
        return ApiResult.success(agentOrchestrator.confirm(request.confirmToken(), principal));
    }

    @Operation(summary = "取消AI待办动作")
    @PostMapping("/agent/cancel")
    @PreAuthorize("hasAuthority('ai:agent')")
    public ApiResult<AgentActionResult> cancelAgentAction(@Valid @RequestBody AgentCancelRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(agentOrchestrator.cancel(request.confirmToken(), principal));
    }

    @Operation(summary = "分页查询AI企业助理工具调用审计")
    @GetMapping("/agent/actions")
    @PreAuthorize("hasAuthority('ai:agent:action:view')")
    public ApiResult<PageResult<AgentActionPageItem>> agentActions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer isWrite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        return ApiResult.success(
                agentActionService.page(page, size, username, toolName, status, isWrite, startTime, endTime)
        );
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

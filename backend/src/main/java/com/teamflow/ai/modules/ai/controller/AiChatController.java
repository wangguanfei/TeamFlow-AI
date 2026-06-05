package com.teamflow.ai.modules.ai.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.security.UserPrincipal;
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

@Tag(name = "AI会话")
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiService aiService;

    public AiChatController(AiService aiService) {
        this.aiService = aiService;
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

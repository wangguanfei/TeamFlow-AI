package com.teamflow.ai.modules.deploy.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.log.Log;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.deploy.dto.DeployCurrentResponse;
import com.teamflow.ai.modules.deploy.dto.DeployRecordItem;
import com.teamflow.ai.modules.deploy.dto.DeployTriggerRequest;
import com.teamflow.ai.modules.deploy.service.DeployService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "部署管理")
@RestController
@RequestMapping("/api/deploy")
public class DeployController {

    private final DeployService deployService;

    public DeployController(DeployService deployService) {
        this.deployService = deployService;
    }

    @Operation(summary = "触发部署")
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('system:deploy:exec')")
    @Log(module = "部署管理", type = "触发部署")
    public ApiResult<Long> trigger(@Valid @RequestBody DeployTriggerRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(deployService.triggerDeploy(request, principal));
    }

    @Operation(summary = "实时日志流（SSE）")
    @GetMapping(value = "/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('system:deploy:exec')")
    public SseEmitter stream(@PathVariable Long id) {
        return deployService.streamLog(id);
    }

    @Operation(summary = "当前运行中的部署")
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('system:deploy:view')")
    public ApiResult<DeployCurrentResponse> current() {
        return ApiResult.success(deployService.currentRunning());
    }

    @Operation(summary = "部署历史分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:deploy:view')")
    public ApiResult<PageResult<DeployRecordItem>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(deployService.page(page, size));
    }
}

package com.teamflow.ai.modules.team.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.team.dto.TeamItem;
import com.teamflow.ai.modules.team.dto.TeamRequest;
import com.teamflow.ai.modules.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "团队")
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @Operation(summary = "创建团队")
    @PostMapping
    @PreAuthorize("hasAuthority('team:create')")
    public ApiResult<TeamItem> create(@Valid @RequestBody TeamRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResult.success(teamService.createTeam(request, principal.getUserId()));
    }

    @Operation(summary = "分页查询团队")
    @GetMapping("/page")
    @PreAuthorize("hasAnyAuthority('team:view','project:view')")
    public ApiResult<PageResult<TeamItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.success(teamService.pageTeams(page, size, keyword));
    }
}

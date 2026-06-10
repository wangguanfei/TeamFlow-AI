package com.teamflow.ai.modules.team.controller;

import com.teamflow.ai.common.api.ApiResult;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.log.Log;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.team.dto.TeamItem;
import com.teamflow.ai.modules.team.dto.TeamMemberItem;
import com.teamflow.ai.modules.team.dto.TeamMemberRequest;
import com.teamflow.ai.modules.team.dto.TeamRequest;
import com.teamflow.ai.modules.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "团队")
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @Log(module = "团队管理", type = "新增")
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return ApiResult.success(teamService.pageTeams(page, size, keyword, status));
    }

    @Log(module = "团队管理", type = "修改")
    @Operation(summary = "更新团队")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('team:update')")
    public ApiResult<TeamItem> update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return ApiResult.success(teamService.updateTeam(id, request));
    }

    @Log(module = "团队管理", type = "状态变更")
    @Operation(summary = "停用/启用团队")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('team:update')")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) throw new BusinessException("status 不能为空");
        teamService.updateStatus(id, status);
        return ApiResult.success();
    }

    @Log(module = "团队管理", type = "删除")
    @Operation(summary = "删除团队")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('team:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ApiResult.success();
    }

    // ---------- 成员管理 ----------

    @Operation(summary = "查询团队成员列表")
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('team:view')")
    public ApiResult<List<TeamMemberItem>> listMembers(@PathVariable Long id) {
        return ApiResult.success(teamService.listMembers(id));
    }

    @Log(module = "团队管理", type = "添加成员")
    @Operation(summary = "添加团队成员")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('team:member')")
    public ApiResult<TeamMemberItem> addMember(@PathVariable Long id,
                                               @Valid @RequestBody TeamMemberRequest request) {
        return ApiResult.success(teamService.addMember(id, request));
    }

    @Log(module = "团队管理", type = "移除成员")
    @Operation(summary = "移除团队成员")
    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasAuthority('team:member')")
    public ApiResult<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        teamService.removeMember(id, memberId);
        return ApiResult.success();
    }

    @Log(module = "团队管理", type = "修改成员角色")
    @Operation(summary = "修改团队成员角色")
    @PatchMapping("/{id}/members/{memberId}/role")
    @PreAuthorize("hasAuthority('team:member')")
    public ApiResult<TeamMemberItem> updateMemberRole(@PathVariable Long id,
                                                      @PathVariable Long memberId,
                                                      @RequestBody Map<String, String> body) {
        String role = body.get("memberRole");
        if (role == null || role.isBlank()) throw new BusinessException("memberRole 不能为空");
        return ApiResult.success(teamService.updateMemberRole(id, memberId, role));
    }
}

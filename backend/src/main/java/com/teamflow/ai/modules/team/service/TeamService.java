package com.teamflow.ai.modules.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.team.dto.TeamItem;
import com.teamflow.ai.modules.team.dto.TeamRequest;
import com.teamflow.ai.modules.team.entity.Team;
import com.teamflow.ai.modules.team.mapper.TeamMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private final TeamMapper teamMapper;
    private final SysUserMapper userMapper;

    public TeamService(TeamMapper teamMapper, SysUserMapper userMapper) {
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
    }

    public PageResult<TeamItem> pageTeams(long page, long size, String keyword) {
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<Team>()
                .eq(Team::getDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(Team::getTeamName, keyword)
                        .or()
                        .like(Team::getTeamCode, keyword)
                        .or()
                        .like(Team::getDescription, keyword))
                .orderByDesc(Team::getId);
        Page<Team> result = teamMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        Map<Long, String> ownerNames = loadUserNames(result.getRecords().stream().map(Team::getOwnerId).toList());
        List<TeamItem> records = result.getRecords().stream()
                .map(team -> toItem(team, ownerNames.get(team.getOwnerId())))
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional
    public TeamItem createTeam(TeamRequest request, Long currentUserId) {
        if (teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getTeamCode, request.teamCode())
                .last("LIMIT 1")) != null) {
            throw new BusinessException("团队编码已存在");
        }
        Long ownerId = request.ownerId() == null ? currentUserId : request.ownerId();
        Team team = new Team();
        team.setTeamName(request.teamName());
        team.setTeamCode(request.teamCode());
        team.setOwnerId(ownerId);
        team.setDescription(request.description());
        team.setStatus(request.status() == null ? 1 : request.status());
        team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now());
        team.setDeleted(0);
        teamMapper.insert(team);
        return toItem(team, loadUserNames(List.of(ownerId)).get(ownerId));
    }

    public Team getTeam(Long id) {
        Team team = teamMapper.selectById(id);
        if (team == null || team.getDeleted() == 1) {
            throw new BusinessException("团队不存在");
        }
        return team;
    }

    private TeamItem toItem(Team team, String ownerName) {
        return new TeamItem(
                team.getId(),
                team.getTeamName(),
                team.getTeamCode(),
                team.getOwnerId(),
                ownerName,
                team.getDescription(),
                team.getStatus(),
                team.getCreatedAt()
        );
    }

    private Map<Long, String> loadUserNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> {
                    String nickname = user.getNickname();
                    return nickname == null || nickname.isBlank() ? user.getUsername() : nickname;
                }));
    }
}

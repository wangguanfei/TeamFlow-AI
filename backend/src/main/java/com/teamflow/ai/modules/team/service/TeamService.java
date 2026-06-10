package com.teamflow.ai.modules.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.project.entity.Project;
import com.teamflow.ai.modules.project.mapper.ProjectMapper;
import com.teamflow.ai.modules.team.dto.TeamItem;
import com.teamflow.ai.modules.team.dto.TeamMemberItem;
import com.teamflow.ai.modules.team.dto.TeamMemberRequest;
import com.teamflow.ai.modules.team.dto.TeamRequest;
import com.teamflow.ai.modules.team.entity.Team;
import com.teamflow.ai.modules.team.entity.TeamMember;
import com.teamflow.ai.modules.team.mapper.TeamMapper;
import com.teamflow.ai.modules.team.mapper.TeamMemberMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper userMapper;

    public TeamService(TeamMapper teamMapper, TeamMemberMapper teamMemberMapper,
                       ProjectMapper projectMapper, SysUserMapper userMapper) {
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
    }

    public PageResult<TeamItem> pageTeams(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<Team>()
                .eq(Team::getDeleted, 0)
                .eq(status != null, Team::getStatus, status)
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
                .eq(Team::getDeleted, 0)
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
        // 创建者自动成为 OWNER 成员
        addMemberInternal(team.getId(), ownerId, "OWNER");
        log.info("创建团队 teamId={} teamCode={} teamName={} ownerId={} 创建人={}",
                team.getId(), team.getTeamCode(), team.getTeamName(), ownerId, currentUserId);
        return toItem(team, loadUserNames(List.of(ownerId)).get(ownerId));
    }

    @Transactional
    public TeamItem updateTeam(Long id, TeamRequest request) {
        Team team = requireTeam(id);
        if (!team.getTeamCode().equals(request.teamCode())) {
            if (teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                    .eq(Team::getTeamCode, request.teamCode())
                    .eq(Team::getDeleted, 0)
                    .ne(Team::getId, id)
                    .last("LIMIT 1")) != null) {
                throw new BusinessException("团队编码已存在");
            }
        }
        team.setTeamName(request.teamName());
        team.setTeamCode(request.teamCode());
        if (request.ownerId() != null) team.setOwnerId(request.ownerId());
        team.setDescription(request.description());
        if (request.status() != null) team.setStatus(request.status());
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.updateById(team);
        log.info("更新团队 teamId={} teamName={}", id, team.getTeamName());
        return toItem(team, loadUserNames(List.of(team.getOwnerId())).get(team.getOwnerId()));
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        Team team = requireTeam(id);
        team.setStatus(status);
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.updateById(team);
        log.info("更新团队状态 teamId={} status={}", id, status);
    }

    @Transactional
    public void deleteTeam(Long id) {
        requireTeam(id);
        long activeProjectCount = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getTeamId, id)
                .eq(Project::getDeleted, 0)
                .ne(Project::getStatus, "ARCHIVED"));
        if (activeProjectCount > 0) {
            throw new BusinessException("团队下还有 " + activeProjectCount + " 个未归档项目，请先归档或迁移");
        }
        teamMapper.deleteById(id);
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, id));
        log.info("删除团队 teamId={}", id);
    }

    // ---------- 成员管理 ----------

    public List<TeamMemberItem> listMembers(Long teamId) {
        requireTeam(teamId);
        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getDeleted, 0)
                        .orderByAsc(TeamMember::getJoinTime));
        if (members.isEmpty()) return List.of();
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(
                members.stream().map(TeamMember::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        return members.stream().map(m -> {
            SysUser u = userMap.get(m.getUserId());
            String username = u == null ? "" : u.getUsername();
            String nickname = u == null ? "" : (u.getNickname() == null ? u.getUsername() : u.getNickname());
            return new TeamMemberItem(m.getId(), m.getTeamId(), m.getUserId(),
                    username, nickname, m.getMemberRole(), m.getJoinTime(), m.getStatus());
        }).toList();
    }

    @Transactional
    public TeamMemberItem addMember(Long teamId, TeamMemberRequest request) {
        requireTeam(teamId);
        boolean exists = teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, request.userId())
                .eq(TeamMember::getDeleted, 0)) > 0;
        if (exists) throw new BusinessException("该用户已是团队成员");
        addMemberInternal(teamId, request.userId(),
                request.memberRole() == null ? "MEMBER" : request.memberRole());
        log.info("团队添加成员 teamId={} userId={} role={}", teamId, request.userId(), request.memberRole());
        return listMembers(teamId).stream()
                .filter(m -> m.userId().equals(request.userId()))
                .findFirst().orElseThrow();
    }

    @Transactional
    public void removeMember(Long teamId, Long memberId) {
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getId, memberId)
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getDeleted, 0));
        if (member == null) throw new BusinessException("成员不存在");
        if ("OWNER".equals(member.getMemberRole())) throw new BusinessException("负责人不能被移除，请先更换负责人");
        teamMemberMapper.deleteById(memberId);
        log.info("团队移除成员 teamId={} memberId={} userId={}", teamId, memberId, member.getUserId());
    }

    @Transactional
    public TeamMemberItem updateMemberRole(Long teamId, Long memberId, String memberRole) {
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getId, memberId)
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getDeleted, 0));
        if (member == null) throw new BusinessException("成员不存在");
        member.setMemberRole(memberRole);
        member.setUpdatedAt(LocalDateTime.now());
        teamMemberMapper.updateById(member);
        SysUser user = userMapper.selectById(member.getUserId());
        String username = user == null ? "" : user.getUsername();
        String nickname = user == null ? "" : (user.getNickname() == null ? user.getUsername() : user.getNickname());
        return new TeamMemberItem(member.getId(), member.getTeamId(), member.getUserId(),
                username, nickname, member.getMemberRole(), member.getJoinTime(), member.getStatus());
    }

    public Team getTeam(Long id) {
        return requireTeam(id);
    }

    // ---------- 内部方法 ----------

    private void addMemberInternal(Long teamId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setJoinTime(LocalDateTime.now());
        member.setStatus(1);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        member.setDeleted(0);
        teamMemberMapper.insert(member);
    }

    private Team requireTeam(Long id) {
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
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> {
                    String nickname = user.getNickname();
                    return nickname == null || nickname.isBlank() ? user.getUsername() : nickname;
                }));
    }
}

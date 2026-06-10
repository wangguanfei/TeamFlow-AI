package com.teamflow.ai.modules.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.cache.DashboardCacheService;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.project.dto.ProjectDetail;
import com.teamflow.ai.modules.project.dto.ProjectListItem;
import com.teamflow.ai.modules.project.dto.ProjectMemberItem;
import com.teamflow.ai.modules.project.dto.ProjectMemberRequest;
import com.teamflow.ai.modules.project.dto.ProjectRequest;
import com.teamflow.ai.modules.project.dto.ProjectStats;
import com.teamflow.ai.modules.project.dto.ProjectTagItem;
import com.teamflow.ai.modules.project.dto.ProjectTagRequest;
import com.teamflow.ai.modules.project.entity.Project;
import com.teamflow.ai.modules.project.entity.ProjectMember;
import com.teamflow.ai.modules.project.entity.ProjectTag;
import com.teamflow.ai.modules.project.mapper.ProjectMapper;
import com.teamflow.ai.modules.project.mapper.ProjectMemberMapper;
import com.teamflow.ai.modules.project.mapper.ProjectTagMapper;
import com.teamflow.ai.modules.team.entity.Team;
import com.teamflow.ai.modules.team.mapper.TeamMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目服务：负责项目本体及其成员、标签的增删改查与统计聚合。
 *
 * <p>对外部状态有改动的关键操作（创建项目、删除项目、增减成员）会记录 INFO 审计日志，
 * 配合 traceId 可还原「谁在什么时间动了哪个项目」。
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectTagMapper projectTagMapper;
    private final TeamMapper teamMapper;
    private final SysUserMapper userMapper;
    private final DashboardCacheService dashboardCache;

    public ProjectService(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            ProjectTagMapper projectTagMapper,
            TeamMapper teamMapper,
            SysUserMapper userMapper,
            DashboardCacheService dashboardCache
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectTagMapper = projectTagMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.dashboardCache = dashboardCache;
    }

    public PageResult<ProjectListItem> pageProjects(long page, long size, String keyword, Long teamId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .eq(Project::getDeleted, 0)
                .eq(teamId != null, Project::getTeamId, teamId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(Project::getProjectName, keyword)
                        .or()
                        .like(Project::getProjectCode, keyword)
                        .or()
                        .like(Project::getDescription, keyword))
                .orderByDesc(Project::getCreatedAt)
                .orderByDesc(Project::getId);
        Page<Project> result = projectMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        List<ProjectListItem> records = buildProjectItems(result.getRecords());
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional
    public ProjectDetail createProject(ProjectRequest request, Long currentUserId) {
        ensureProjectCodeAvailable(request.projectCode(), null);
        ensureTeamExists(request.teamId());
        Long ownerId = request.ownerId() == null ? currentUserId : request.ownerId();
        ensureUserExists(ownerId);

        Project project = new Project();
        fillProject(project, request, ownerId);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        project.setDeleted(0);
        projectMapper.insert(project);

        saveMember(project.getId(), ownerId, "PM");
        if (request.memberUserIds() != null) {
            for (Long userId : request.memberUserIds().stream().distinct().toList()) {
                if (!userId.equals(ownerId)) {
                    saveMember(project.getId(), userId, "DEV");
                }
            }
        }
        if (request.tags() != null) {
            for (ProjectTagRequest tag : request.tags()) {
                saveTag(project.getId(), tag.tagName(), tag.tagColor());
            }
        }
        log.info("创建项目 projectId={} code={} name={} ownerId={} 创建人={}",
                project.getId(), project.getProjectCode(), project.getProjectName(), ownerId, currentUserId);
        dashboardCache.evictProjectStats();
        return getProject(project.getId());
    }

    public ProjectDetail getProject(Long id) {
        Project project = getProjectEntity(id);
        ProjectListItem item = buildProjectItems(List.of(project)).get(0);
        return new ProjectDetail(item, listMembers(id), listTags(id));
    }

    @Transactional
    public ProjectDetail updateProject(Long id, ProjectRequest request) {
        Project project = getProjectEntity(id);
        ensureProjectCodeAvailable(request.projectCode(), id);
        ensureTeamExists(request.teamId());
        Long ownerId = request.ownerId() == null ? project.getOwnerId() : request.ownerId();
        ensureUserExists(ownerId);
        fillProject(project, request, ownerId);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        saveMember(project.getId(), ownerId, "PM");
        log.info("更新项目 projectId={} code={} name={} ownerId={}",
                project.getId(), project.getProjectCode(), project.getProjectName(), ownerId);
        dashboardCache.evictProjectStats();
        return getProject(project.getId());
    }

    @Transactional
    public void deleteProject(Long id) {
        getProjectEntity(id);
        projectMapper.deleteById(id);
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        projectTagMapper.delete(new LambdaQueryWrapper<ProjectTag>().eq(ProjectTag::getProjectId, id));
        log.info("删除项目（含成员与标签）projectId={}", id);
        dashboardCache.evictProjectStats();
    }

    public ProjectStats stats() {
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>().eq(Project::getDeleted, 0));
        long active = projects.stream().filter(project -> "ACTIVE".equals(project.getStatus())).count();
        long done = projects.stream().filter(project -> "DONE".equals(project.getStatus())).count();
        BigDecimal totalProgress = projects.stream()
                .map(project -> project.getProgress() == null ? BigDecimal.ZERO : project.getProgress())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = projects.isEmpty()
                ? BigDecimal.ZERO
                : totalProgress.divide(BigDecimal.valueOf(projects.size()), 2, RoundingMode.HALF_UP);
        return new ProjectStats(projects.size(), active, done, average);
    }

    public PageResult<ProjectMemberItem> pageMembers(long page, long size, Long projectId, String keyword) {
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getDeleted, 0)
                .eq(projectId != null, ProjectMember::getProjectId, projectId)
                .orderByDesc(ProjectMember::getId);
        Page<ProjectMember> result = projectMemberMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        List<ProjectMemberItem> members = toMemberItems(result.getRecords()).stream()
                .filter(member -> keyword == null || keyword.isBlank()
                        || contains(member.username(), keyword)
                        || contains(member.nickname(), keyword)
                        || contains(member.email(), keyword)
                        || contains(member.projectRole(), keyword))
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), members);
    }

    public List<ProjectMemberItem> listMembers(Long projectId) {
        getProjectEntity(projectId);
        List<ProjectMember> members = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getDeleted, 0)
                .eq(ProjectMember::getProjectId, projectId)
                .orderByAsc(ProjectMember::getId));
        return toMemberItems(members);
    }

    @Transactional
    public ProjectMemberItem createMember(ProjectMemberRequest request) {
        getProjectEntity(request.projectId());
        ProjectMember member = saveMember(request.projectId(), request.userId(), request.projectRole());
        log.info("添加项目成员 projectId={} userId={} projectRole={}",
                request.projectId(), request.userId(), member.getProjectRole());
        return toMemberItems(List.of(member)).get(0);
    }

    @Transactional
    public ProjectMemberItem updateMember(Long id, ProjectMemberRequest request) {
        ProjectMember member = getProjectMemberEntity(id);
        getProjectEntity(request.projectId());
        ensureUserExists(request.userId());
        member.setProjectId(request.projectId());
        member.setUserId(request.userId());
        member.setProjectRole(normalizeProjectRole(request.projectRole()));
        projectMemberMapper.updateById(member);
        log.info("更新项目成员 memberId={} projectId={} userId={} projectRole={}",
                id, request.projectId(), request.userId(), member.getProjectRole());
        return toMemberItems(List.of(member)).get(0);
    }

    @Transactional
    public void deleteMember(Long id) {
        getProjectMemberEntity(id);
        projectMemberMapper.deleteById(id);
        log.info("移除项目成员 memberId={}", id);
    }

    public PageResult<ProjectTagItem> pageTags(long page, long size, Long projectId, String keyword) {
        LambdaQueryWrapper<ProjectTag> wrapper = new LambdaQueryWrapper<ProjectTag>()
                .eq(projectId != null, ProjectTag::getProjectId, projectId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(ProjectTag::getTagName, keyword)
                        .or()
                        .like(ProjectTag::getTagColor, keyword))
                .orderByDesc(ProjectTag::getId);
        Page<ProjectTag> result = projectTagMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        List<ProjectTagItem> records = result.getRecords().stream().map(this::toTagItem).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    public List<ProjectTagItem> listTags(Long projectId) {
        getProjectEntity(projectId);
        return projectTagMapper.selectList(new LambdaQueryWrapper<ProjectTag>()
                        .eq(ProjectTag::getProjectId, projectId)
                        .orderByAsc(ProjectTag::getId))
                .stream()
                .map(this::toTagItem)
                .toList();
    }

    @Transactional
    public ProjectTagItem createTag(ProjectTagRequest request) {
        getProjectEntity(request.projectId());
        ProjectTag tag = saveTag(request.projectId(), request.tagName(), request.tagColor());
        log.info("创建项目标签 tagId={} projectId={} tagName={}", tag.getId(), request.projectId(), request.tagName());
        return toTagItem(tag);
    }

    @Transactional
    public ProjectTagItem updateTag(Long id, ProjectTagRequest request) {
        ProjectTag tag = getProjectTagEntity(id);
        getProjectEntity(request.projectId());
        tag.setProjectId(request.projectId());
        tag.setTagName(request.tagName());
        tag.setTagColor(defaultTagColor(request.tagColor()));
        projectTagMapper.updateById(tag);
        log.info("更新项目标签 tagId={} projectId={} tagName={}", id, request.projectId(), request.tagName());
        return toTagItem(tag);
    }

    @Transactional
    public void deleteTag(Long id) {
        getProjectTagEntity(id);
        projectTagMapper.deleteById(id);
        log.info("删除项目标签 tagId={}", id);
    }

    private void fillProject(Project project, ProjectRequest request, Long ownerId) {
        project.setTeamId(request.teamId());
        project.setProjectCode(request.projectCode());
        project.setProjectName(request.projectName());
        project.setDescription(request.description());
        project.setOwnerId(ownerId);
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(request.status() == null || request.status().isBlank() ? "PLANNING" : request.status());
        project.setProgress(request.progress() == null ? BigDecimal.ZERO : request.progress());
    }

    private List<ProjectListItem> buildProjectItems(List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, Team> teams = loadTeams(projects.stream().map(Project::getTeamId).toList());
        Map<Long, String> userNames = loadUserNames(projects.stream().map(Project::getOwnerId).toList());
        Map<Long, List<ProjectTagItem>> tags = projectTagMapper.selectList(new LambdaQueryWrapper<ProjectTag>()
                        .in(ProjectTag::getProjectId, projectIds))
                .stream()
                .map(this::toTagItem)
                .collect(Collectors.groupingBy(ProjectTagItem::projectId));
        Map<Long, Long> memberCounts = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getDeleted, 0)
                        .in(ProjectMember::getProjectId, projectIds))
                .stream()
                .collect(Collectors.groupingBy(ProjectMember::getProjectId, Collectors.counting()));

        List<ProjectListItem> items = new ArrayList<>();
        for (Project project : projects) {
            Team team = teams.get(project.getTeamId());
            items.add(new ProjectListItem(
                    project.getId(),
                    project.getTeamId(),
                    team == null ? null : team.getTeamName(),
                    project.getProjectCode(),
                    project.getProjectName(),
                    project.getDescription(),
                    project.getOwnerId(),
                    userNames.get(project.getOwnerId()),
                    project.getStartDate(),
                    project.getEndDate(),
                    project.getStatus(),
                    project.getProgress(),
                    memberCounts.getOrDefault(project.getId(), 0L),
                    tags.getOrDefault(project.getId(), List.of()),
                    project.getCreatedAt(),
                    project.getUpdatedAt()
            ));
        }
        return items;
    }

    private ProjectMember saveMember(Long projectId, Long userId, String projectRole) {
        ensureUserExists(userId);
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null) {
            member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(userId);
            member.setProjectRole(normalizeProjectRole(projectRole));
            member.setCreatedAt(LocalDateTime.now());
            member.setDeleted(0);
            projectMemberMapper.insert(member);
            return member;
        }
        if (member.getDeleted() != null && member.getDeleted() == 1) {
            member.setDeleted(0);
            member.setCreatedAt(LocalDateTime.now());
        }
        member.setProjectRole(normalizeProjectRole(projectRole));
        projectMemberMapper.updateById(member);
        return member;
    }

    private ProjectTag saveTag(Long projectId, String tagName, String tagColor) {
        ProjectTag tag = new ProjectTag();
        tag.setProjectId(projectId);
        tag.setTagName(tagName);
        tag.setTagColor(defaultTagColor(tagColor));
        tag.setCreatedAt(LocalDateTime.now());
        projectTagMapper.insert(tag);
        return tag;
    }

    private List<ProjectMemberItem> toMemberItems(List<ProjectMember> members) {
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(members.stream().map(ProjectMember::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return members.stream()
                .map(member -> {
                    SysUser user = users.get(member.getUserId());
                    return new ProjectMemberItem(
                            member.getId(),
                            member.getProjectId(),
                            member.getUserId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getNickname(),
                            user == null ? null : user.getEmail(),
                            member.getProjectRole(),
                            member.getCreatedAt()
                    );
                })
                .toList();
    }

    private ProjectTagItem toTagItem(ProjectTag tag) {
        return new ProjectTagItem(tag.getId(), tag.getProjectId(), tag.getTagName(), tag.getTagColor(), tag.getCreatedAt());
    }

    private Project getProjectEntity(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null || project.getDeleted() == 1) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    private ProjectMember getProjectMemberEntity(Long id) {
        ProjectMember member = projectMemberMapper.selectById(id);
        if (member == null || member.getDeleted() == 1) {
            throw new BusinessException("项目成员不存在");
        }
        return member;
    }

    private ProjectTag getProjectTagEntity(Long id) {
        ProjectTag tag = projectTagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException("项目标签不存在");
        }
        return tag;
    }

    private void ensureProjectCodeAvailable(String projectCode, Long currentProjectId) {
        Project existing = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectCode, projectCode)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentProjectId)) {
            throw new BusinessException("项目编码已存在");
        }
    }

    private void ensureTeamExists(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getDeleted() == 1) {
            throw new BusinessException("团队不存在");
        }
    }

    private void ensureUserExists(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("用户不存在");
        }
    }

    private Map<Long, Team> loadTeams(List<Long> teamIds) {
        List<Long> ids = teamIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return teamMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
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

    private String normalizeProjectRole(String projectRole) {
        return projectRole == null || projectRole.isBlank() ? "DEV" : projectRole;
    }

    private String defaultTagColor(String tagColor) {
        return tagColor == null || tagColor.isBlank() ? "#2563EB" : tagColor;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }
}

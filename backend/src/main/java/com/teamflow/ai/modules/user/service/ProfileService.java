package com.teamflow.ai.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.ai.entity.AiSession;
import com.teamflow.ai.modules.ai.mapper.AiSessionMapper;
import com.teamflow.ai.modules.file.entity.FileInfo;
import com.teamflow.ai.modules.file.dto.FileContent;
import com.teamflow.ai.modules.file.dto.FileItem;
import com.teamflow.ai.modules.file.mapper.FileInfoMapper;
import com.teamflow.ai.modules.file.service.FileService;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeDoc;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeDocMapper;
import com.teamflow.ai.modules.project.entity.Project;
import com.teamflow.ai.modules.project.mapper.ProjectMapper;
import com.teamflow.ai.modules.system.service.PermissionQueryService;
import com.teamflow.ai.modules.task.entity.Task;
import com.teamflow.ai.modules.task.entity.TaskExecutor;
import com.teamflow.ai.modules.task.mapper.TaskExecutorMapper;
import com.teamflow.ai.modules.task.mapper.TaskMapper;
import com.teamflow.ai.modules.user.dto.ProfileOverviewResponse;
import com.teamflow.ai.modules.user.dto.ProfilePasswordRequest;
import com.teamflow.ai.modules.user.dto.ProfileResponse;
import com.teamflow.ai.modules.user.dto.ProfileUpdateRequest;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/avif"
    );

    private final SysUserMapper userMapper;
    private final TaskMapper taskMapper;
    private final TaskExecutorMapper taskExecutorMapper;
    private final ProjectMapper projectMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final FileInfoMapper fileInfoMapper;
    private final FileService fileService;
    private final AiSessionMapper aiSessionMapper;
    private final PermissionQueryService permissionQueryService;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
            SysUserMapper userMapper,
            TaskMapper taskMapper,
            TaskExecutorMapper taskExecutorMapper,
            ProjectMapper projectMapper,
            KnowledgeDocMapper knowledgeDocMapper,
            FileInfoMapper fileInfoMapper,
            FileService fileService,
            AiSessionMapper aiSessionMapper,
            PermissionQueryService permissionQueryService,
            PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.taskMapper = taskMapper;
        this.taskExecutorMapper = taskExecutorMapper;
        this.projectMapper = projectMapper;
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.fileInfoMapper = fileInfoMapper;
        this.fileService = fileService;
        this.aiSessionMapper = aiSessionMapper;
        this.permissionQueryService = permissionQueryService;
        this.passwordEncoder = passwordEncoder;
    }

    public ProfileResponse getProfile(Long userId) {
        return toResponse(getActiveUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        SysUser user = getActiveUser(userId);
        user.setNickname(defaultIfBlank(request.nickname(), user.getUsername()));
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setEmail(blankToNull(request.email()));
        user.setMobile(blankToNull(request.mobile()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("更新个人资料 userId={} nickname={}", user.getId(), user.getNickname());
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException(413, "头像文件不能超过 5MB");
        }
        if (!isAllowedAvatarContentType(file.getContentType())) {
            throw new BusinessException("头像仅支持 png、jpg、gif、webp、bmp、avif 图片");
        }
        SysUser user = getActiveUser(userId);
        FileItem uploaded = fileService.upload(file, "PROFILE_AVATAR", userId, userId);
        user.setAvatarUrl("/api/profile/avatar-file/" + uploaded.id());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("更新头像 userId={} fileId={}", user.getId(), uploaded.id());
        return toResponse(user);
    }

    public FileContent loadAvatarFile(Long fileId) {
        FileInfo file = fileInfoMapper.selectById(fileId);
        if (file == null
                || Integer.valueOf(1).equals(file.getDeleted())
                || !"PROFILE_AVATAR".equals(file.getBizType())
                || !isAllowedAvatarContentType(file.getContentType())) {
            throw new BusinessException(404, "头像不存在");
        }
        return fileService.loadContent(fileId);
    }

    @Transactional
    public void updatePassword(Long userId, ProfilePasswordRequest request) {
        SysUser user = getActiveUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        // 安全敏感操作：仅记录修改人身份，绝不记录任何密码明文/密文
        log.warn("用户修改本人密码 userId={} username={}", user.getId(), user.getUsername());
    }

    public ProfileOverviewResponse getOverview(Long userId) {
        Long responsibleTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, userId)
                .eq(Task::getDeleted, 0));
        Long executingTaskCount = countExecutingTasks(userId);
        Long ownedProjectCount = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getOwnerId, userId)
                .eq(Project::getDeleted, 0));
        Long knowledgeDocCount = knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getAuthorId, userId)
                .eq(KnowledgeDoc::getDeleted, 0));
        Long fileCount = fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUploaderId, userId)
                .eq(FileInfo::getDeleted, 0));
        Long aiSessionCount = aiSessionMapper.selectCount(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .eq(AiSession::getDeleted, 0));
        return new ProfileOverviewResponse(
                responsibleTaskCount,
                executingTaskCount,
                ownedProjectCount,
                knowledgeDocCount,
                fileCount,
                aiSessionCount,
                (long) permissionQueryService.listRoleCodes(userId).size(),
                (long) permissionQueryService.listPermissionCodes(userId).size()
        );
    }

    private Long countExecutingTasks(Long userId) {
        List<Long> taskIds = taskExecutorMapper.selectList(new LambdaQueryWrapper<TaskExecutor>()
                        .eq(TaskExecutor::getUserId, userId))
                .stream()
                .map(TaskExecutor::getTaskId)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return 0L;
        }
        return taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .in(Task::getId, taskIds)
                .eq(Task::getDeleted, 0));
    }

    private SysUser getActiveUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BusinessException(401, "账号不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用");
        }
        return user;
    }

    private ProfileResponse toResponse(SysUser user) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getEmail(),
                user.getMobile(),
                user.getStatus(),
                user.getLastLoginTime(),
                user.getLastLoginIp(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                permissionQueryService.listRoleCodes(user.getId()),
                permissionQueryService.listPermissionCodes(user.getId())
        );
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isAllowedAvatarContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        return ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }
}

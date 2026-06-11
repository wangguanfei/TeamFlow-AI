package com.teamflow.ai.modules.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.cache.DashboardCacheService;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.notification.dto.NotificationRequest;
import com.teamflow.ai.modules.notification.service.NotificationService;
import com.teamflow.ai.modules.file.entity.FileInfo;
import com.teamflow.ai.modules.file.mapper.FileInfoMapper;
import com.teamflow.ai.modules.project.entity.Project;
import com.teamflow.ai.modules.project.mapper.ProjectMapper;
import com.teamflow.ai.modules.task.dto.GanttTaskItem;
import com.teamflow.ai.modules.task.dto.KanbanColumn;
import com.teamflow.ai.modules.task.dto.TaskAttachmentItem;
import com.teamflow.ai.modules.task.dto.TaskAttachmentRequest;
import com.teamflow.ai.modules.task.dto.TaskCommentItem;
import com.teamflow.ai.modules.task.dto.TaskCommentRequest;
import com.teamflow.ai.modules.task.dto.TaskDetail;
import com.teamflow.ai.modules.task.dto.TaskExecutorItem;
import com.teamflow.ai.modules.task.dto.TaskListItem;
import com.teamflow.ai.modules.task.dto.TaskRequest;
import com.teamflow.ai.modules.task.dto.TaskStatusRequest;
import com.teamflow.ai.modules.task.dto.TaskTagItem;
import com.teamflow.ai.modules.task.dto.TaskTagRequest;
import com.teamflow.ai.modules.task.dto.TaskWorklogItem;
import com.teamflow.ai.modules.task.dto.TaskWorklogRequest;
import com.teamflow.ai.modules.task.entity.Task;
import com.teamflow.ai.modules.task.entity.TaskAttachment;
import com.teamflow.ai.modules.task.entity.TaskComment;
import com.teamflow.ai.modules.task.entity.TaskExecutor;
import com.teamflow.ai.modules.task.entity.TaskTag;
import com.teamflow.ai.modules.task.entity.TaskWorklog;
import com.teamflow.ai.modules.task.mapper.TaskAttachmentMapper;
import com.teamflow.ai.modules.task.mapper.TaskCommentMapper;
import com.teamflow.ai.modules.task.mapper.TaskExecutorMapper;
import com.teamflow.ai.modules.task.mapper.TaskMapper;
import com.teamflow.ai.modules.task.mapper.TaskTagMapper;
import com.teamflow.ai.modules.task.mapper.TaskWorklogMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务服务：覆盖任务的看板/列表/甘特视图、状态流转，以及评论、工时、附件、标签等子实体。
 *
 * <p>创建/删除任务、状态流转等会改变协作状态的操作记录 INFO 审计日志，
 * 任务指派还会触发站内通知（见 {@link NotificationService}）。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final List<String> KANBAN_STATUSES = List.of("TODO", "DOING", "TESTING", "DONE");

    private final TaskMapper taskMapper;
    private final TaskCommentMapper commentMapper;
    private final TaskWorklogMapper worklogMapper;
    private final TaskAttachmentMapper attachmentMapper;
    private final TaskTagMapper tagMapper;
    private final TaskExecutorMapper executorMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final FileInfoMapper fileInfoMapper;
    private final NotificationService notificationService;
    private final DashboardCacheService dashboardCache;

    public TaskService(
            TaskMapper taskMapper,
            TaskCommentMapper commentMapper,
            TaskWorklogMapper worklogMapper,
            TaskAttachmentMapper attachmentMapper,
            TaskTagMapper tagMapper,
            TaskExecutorMapper executorMapper,
            ProjectMapper projectMapper,
            SysUserMapper userMapper,
            FileInfoMapper fileInfoMapper,
            NotificationService notificationService,
            DashboardCacheService dashboardCache
    ) {
        this.taskMapper = taskMapper;
        this.commentMapper = commentMapper;
        this.worklogMapper = worklogMapper;
        this.attachmentMapper = attachmentMapper;
        this.tagMapper = tagMapper;
        this.executorMapper = executorMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.fileInfoMapper = fileInfoMapper;
        this.notificationService = notificationService;
        this.dashboardCache = dashboardCache;
    }

    public PageResult<TaskListItem> pageTasks(long page, long size, Long projectId, String status, String keyword, Long teamId) {
        LambdaQueryWrapper<Task> wrapper = baseTaskWrapper(resolveProjectIds(projectId, teamId), status, keyword)
                .orderByDesc(Task::getCreatedAt)
                .orderByDesc(Task::getId);
        Page<Task> result = taskMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                buildTaskItems(result.getRecords())
        );
    }

    public List<KanbanColumn> kanban(Long projectId, String keyword, Long teamId) {
        List<TaskListItem> tasks = buildTaskItems(taskMapper.selectList(
                baseTaskWrapper(resolveProjectIds(projectId, teamId), null, keyword)
                .in(Task::getStatus, KANBAN_STATUSES)
                .orderByDesc(Task::getCreatedAt)
                .orderByDesc(Task::getId)));
        Map<String, List<TaskListItem>> byStatus = tasks.stream().collect(Collectors.groupingBy(TaskListItem::status));
        return KANBAN_STATUSES.stream()
                .map(status -> new KanbanColumn(status, statusTitle(status), byStatus.getOrDefault(status, List.of())))
                .toList();
    }

    public List<GanttTaskItem> gantt(Long projectId, String keyword, Long teamId) {
        return buildTaskItems(taskMapper.selectList(baseTaskWrapper(resolveProjectIds(projectId, teamId), null, keyword)
                        .orderByDesc(Task::getCreatedAt)
                        .orderByDesc(Task::getId)))
                .stream()
                .map(task -> new GanttTaskItem(
                        task.id(),
                        task.taskNo(),
                        task.title(),
                        task.status(),
                        task.assigneeName(),
                        task.startTime(),
                        task.dueTime(),
                        progressByStatus(task.status())
                ))
                .toList();
    }

    @Transactional
    public TaskDetail createTask(TaskRequest request, Long currentUserId) {
        Project project = getProject(request.projectId());
        Long assigneeId = request.assigneeId() == null ? currentUserId : request.assigneeId();
        ensureUserExists(assigneeId);

        Task task = new Task();
        fillTask(task, request, currentUserId, project);
        task.setAssigneeId(assigneeId);
        task.setReporterId(currentUserId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setActualHours(BigDecimal.ZERO);
        task.setDeleted(0);
        taskMapper.insert(task);

        if (request.tags() != null) {
            for (TaskTagRequest tag : request.tags()) {
                saveTag(task.getId(), tag.tagName(), tag.tagColor());
            }
        }
        List<Long> executorIds = normalizeExecutorIds(request.executorIds(), assigneeId);
        saveExecutors(task.getId(), executorIds);
        notifyTaskOwnerChanged(task, assigneeId, currentUserId, "你成为任务负责人");
        notifyTaskExecutorsAdded(task, executorIds, currentUserId);
        log.info("创建任务 taskId={} projectId={} assigneeId={} 创建人={}",
                task.getId(), request.projectId(), assigneeId, currentUserId);
        dashboardCache.evictTaskStats();
        return getTask(task.getId());
    }

    public TaskDetail getTask(Long id) {
        Task task = getTaskEntity(id);
        return new TaskDetail(
                buildTaskItems(List.of(task)).get(0),
                listExecutors(id),
                listComments(id),
                listWorklogs(id),
                listAttachments(id),
                listTags(id)
        );
    }

    @Transactional
    public TaskDetail updateTask(Long id, TaskRequest request, Long currentUserId) {
        Task task = getTaskEntity(id);
        Long oldAssigneeId = task.getAssigneeId();
        Set<Long> oldExecutorIds = new LinkedHashSet<>(listExecutorIds(id));
        Project project = getProject(request.projectId());
        Long assigneeId = request.assigneeId() == null ? currentUserId : request.assigneeId();
        ensureUserExists(assigneeId);
        fillTask(task, request, currentUserId, project);
        task.setAssigneeId(assigneeId);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        if (oldAssigneeId == null || !oldAssigneeId.equals(assigneeId)) {
            notifyTaskOwnerChanged(task, assigneeId, currentUserId, "任务负责人已变更给你");
        }
        if (request.executorIds() != null) {
            List<Long> executorIds = normalizeExecutorIds(request.executorIds(), null);
            saveExecutors(task.getId(), executorIds);
            List<Long> addedExecutorIds = executorIds.stream()
                    .filter(executorId -> !oldExecutorIds.contains(executorId))
                    .toList();
            notifyTaskExecutorsAdded(task, addedExecutorIds, currentUserId);
        }
        log.info("更新任务 taskId={} assignee {} -> {} 操作人={}", id, oldAssigneeId, assigneeId, currentUserId);
        dashboardCache.evictTaskStats();
        return getTask(task.getId());
    }

    @Transactional
    public TaskListItem updateStatus(Long id, TaskStatusRequest request) {
        Task task = getTaskEntity(id);
        String oldStatus = task.getStatus();
        task.setStatus(request.status());
        task.setSortNo(request.sortNo() == null ? task.getSortNo() : request.sortNo());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务状态流转 taskId={} {} -> {}", id, oldStatus, request.status());
        dashboardCache.evictTaskStats();
        return buildTaskItems(List.of(task)).get(0);
    }

    @Transactional
    public void deleteTask(Long id) {
        getTaskEntity(id);
        taskMapper.deleteById(id);
        commentMapper.delete(new LambdaQueryWrapper<TaskComment>().eq(TaskComment::getTaskId, id));
        worklogMapper.delete(new LambdaQueryWrapper<TaskWorklog>().eq(TaskWorklog::getTaskId, id));
        tagMapper.delete(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, id));
        attachmentMapper.delete(new LambdaQueryWrapper<TaskAttachment>().eq(TaskAttachment::getTaskId, id));
        executorMapper.delete(new LambdaQueryWrapper<TaskExecutor>().eq(TaskExecutor::getTaskId, id));
        log.info("删除任务（含评论/工时/标签/附件/执行人）taskId={}", id);
        dashboardCache.evictTaskStats();
    }

    @Transactional
    public TaskCommentItem createComment(TaskCommentRequest request, Long currentUserId) {
        Task task = getTaskEntity(request.taskId());
        TaskComment comment = new TaskComment();
        comment.setTaskId(request.taskId());
        comment.setUserId(currentUserId);
        comment.setContent(request.content());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setDeleted(0);
        commentMapper.insert(comment);
        notifyTaskCommented(task, currentUserId);
        log.info("发表任务评论 commentId={} taskId={} 评论人={}", comment.getId(), request.taskId(), currentUserId);
        return toCommentItems(List.of(comment)).get(0);
    }

    public PageResult<TaskCommentItem> pageComments(long page, long size, Long taskId, String keyword) {
        LambdaQueryWrapper<TaskComment> wrapper = new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getDeleted, 0)
                .eq(taskId != null, TaskComment::getTaskId, taskId)
                .like(keyword != null && !keyword.isBlank(), TaskComment::getContent, keyword)
                .orderByDesc(TaskComment::getId);
        Page<TaskComment> result = commentMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toCommentItems(result.getRecords()));
    }

    @Transactional
    public void deleteComment(Long id) {
        commentMapper.deleteById(id);
        log.info("删除任务评论 commentId={}", id);
    }

    @Transactional
    public TaskWorklogItem createWorklog(TaskWorklogRequest request, Long currentUserId) {
        Task task = getTaskEntity(request.taskId());
        TaskWorklog worklog = new TaskWorklog();
        worklog.setTaskId(request.taskId());
        worklog.setUserId(currentUserId);
        worklog.setWorkDate(request.workDate() == null ? LocalDate.now() : request.workDate());
        worklog.setHours(request.hours());
        worklog.setDescription(request.description());
        worklog.setCreatedAt(LocalDateTime.now());
        worklog.setUpdatedAt(LocalDateTime.now());
        worklog.setDeleted(0);
        worklogMapper.insert(worklog);
        refreshActualHours(task.getId());
        log.info("登记任务工时 worklogId={} taskId={} 工时={}h 登记人={}",
                worklog.getId(), request.taskId(), request.hours(), currentUserId);
        return toWorklogItems(List.of(worklog)).get(0);
    }

    public PageResult<TaskWorklogItem> pageWorklogs(long page, long size, Long taskId, String keyword) {
        LambdaQueryWrapper<TaskWorklog> wrapper = new LambdaQueryWrapper<TaskWorklog>()
                .eq(TaskWorklog::getDeleted, 0)
                .eq(taskId != null, TaskWorklog::getTaskId, taskId)
                .like(keyword != null && !keyword.isBlank(), TaskWorklog::getDescription, keyword)
                .orderByDesc(TaskWorklog::getId);
        Page<TaskWorklog> result = worklogMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toWorklogItems(result.getRecords()));
    }

    @Transactional
    public void deleteWorklog(Long id) {
        TaskWorklog worklog = worklogMapper.selectById(id);
        if (worklog == null) {
            return;
        }
        Long taskId = worklog.getTaskId();
        worklogMapper.deleteById(id);
        refreshActualHours(taskId);
        log.info("删除任务工时 worklogId={} taskId={}", id, taskId);
    }

    @Transactional
    public TaskAttachmentItem createAttachment(TaskAttachmentRequest request, Long currentUserId) {
        getTaskEntity(request.taskId());
        ensureFileExists(request.fileId());
        TaskAttachment attachment = new TaskAttachment();
        attachment.setTaskId(request.taskId());
        attachment.setFileId(request.fileId());
        attachment.setCreatedBy(currentUserId);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);
        log.info("添加任务附件 attachmentId={} taskId={} fileId={} 操作人={}",
                attachment.getId(), request.taskId(), request.fileId(), currentUserId);
        return toAttachmentItems(List.of(attachment)).get(0);
    }

    public PageResult<TaskAttachmentItem> pageAttachments(long page, long size, Long taskId) {
        LambdaQueryWrapper<TaskAttachment> wrapper = new LambdaQueryWrapper<TaskAttachment>()
                .eq(taskId != null, TaskAttachment::getTaskId, taskId)
                .orderByDesc(TaskAttachment::getId);
        Page<TaskAttachment> result = attachmentMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toAttachmentItems(result.getRecords()));
    }

    @Transactional
    public void deleteAttachment(Long id) {
        attachmentMapper.deleteById(id);
        log.info("删除任务附件 attachmentId={}", id);
    }

    @Transactional
    public TaskTagItem createTag(TaskTagRequest request) {
        getTaskEntity(request.taskId());
        TaskTag tag = saveTag(request.taskId(), request.tagName(), request.tagColor());
        log.info("创建任务标签 tagId={} taskId={} tagName={}", tag.getId(), request.taskId(), request.tagName());
        return toTagItem(tag);
    }

    public PageResult<TaskTagItem> pageTags(long page, long size, Long taskId, String keyword) {
        LambdaQueryWrapper<TaskTag> wrapper = new LambdaQueryWrapper<TaskTag>()
                .eq(taskId != null, TaskTag::getTaskId, taskId)
                .like(keyword != null && !keyword.isBlank(), TaskTag::getTagName, keyword)
                .orderByDesc(TaskTag::getId);
        Page<TaskTag> result = tagMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream().map(this::toTagItem).toList());
    }

    @Transactional
    public void deleteTag(Long id) {
        tagMapper.deleteById(id);
        log.info("删除任务标签 tagId={}", id);
    }

    private LambdaQueryWrapper<Task> baseTaskWrapper(List<Long> projectIds, String status, String keyword) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getDeleted, 0)
                .eq(status != null && !status.isBlank(), Task::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(Task::getTaskNo, keyword)
                        .or()
                        .like(Task::getTitle, keyword)
                        .or()
                        .like(Task::getDescription, keyword));
        if (projectIds != null && projectIds.size() == 1) {
            wrapper.eq(Task::getProjectId, projectIds.get(0));
        } else if (projectIds != null && !projectIds.isEmpty()) {
            wrapper.in(Task::getProjectId, projectIds);
        }
        return wrapper;
    }

    private List<Long> resolveProjectIds(Long projectId, Long teamId) {
        if (projectId != null) return List.of(projectId);
        if (teamId != null) {
            List<Long> ids = projectMapper.selectList(
                    new LambdaQueryWrapper<Project>()
                            .eq(Project::getTeamId, teamId)
                            .eq(Project::getDeleted, 0)
                            .select(Project::getId))
                    .stream().map(Project::getId).toList();
            return ids.isEmpty() ? List.of(-1L) : ids;
        }
        return null;
    }

    private void fillTask(Task task, TaskRequest request, Long currentUserId, Project project) {
        task.setProjectId(request.projectId());
        task.setParentId(request.parentId() == null ? 0L : request.parentId());
        task.setTaskNo(request.taskNo() == null || request.taskNo().isBlank()
                ? project.getProjectCode() + "-" + System.currentTimeMillis() % 100000
                : request.taskNo());
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority() == null || request.priority().isBlank() ? "MEDIUM" : request.priority());
        task.setStatus(request.status() == null || request.status().isBlank() ? "TODO" : request.status());
        task.setStartTime(request.startTime());
        task.setDueTime(request.dueTime());
        task.setEstimateHours(request.estimateHours() == null ? BigDecimal.ZERO : request.estimateHours());
        task.setSortNo(request.sortNo() == null ? (task.getSortNo() == null ? 1 : task.getSortNo()) : request.sortNo());
        if (task.getReporterId() == null) {
            task.setReporterId(currentUserId);
        }
    }

    private List<TaskListItem> buildTaskItems(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, Project> projects = projectMapper.selectBatchIds(tasks.stream().map(Task::getProjectId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        List<Long> userIds = tasks.stream()
                .flatMap(task -> List.of(task.getAssigneeId(), task.getReporterId()).stream())
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, String> userNames = loadUserNames(userIds);
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskExecutor> executors = executorMapper.selectList(new LambdaQueryWrapper<TaskExecutor>()
                .in(TaskExecutor::getTaskId, taskIds)
                .orderByAsc(TaskExecutor::getId));
        Map<Long, List<Long>> executorIdsByTaskId = executors.stream()
                .collect(Collectors.groupingBy(
                        TaskExecutor::getTaskId,
                        Collectors.mapping(TaskExecutor::getUserId, Collectors.toList())
                ));
        Map<Long, String> executorUserNames = loadUserNames(executors.stream().map(TaskExecutor::getUserId).toList());
        Map<Long, List<TaskTagItem>> tags = tagMapper.selectList(new LambdaQueryWrapper<TaskTag>()
                        .in(TaskTag::getTaskId, taskIds))
                .stream()
                .map(this::toTagItem)
                .collect(Collectors.groupingBy(TaskTagItem::taskId));
        return tasks.stream()
                .map(task -> {
                    Project project = projects.get(task.getProjectId());
                    return new TaskListItem(
                            task.getId(),
                            task.getProjectId(),
                            project == null ? null : project.getProjectName(),
                            task.getParentId(),
                            task.getTaskNo(),
                            task.getTitle(),
                            task.getDescription(),
                            task.getAssigneeId(),
                            userNames.get(task.getAssigneeId()),
                            executorIdsByTaskId.getOrDefault(task.getId(), List.of()),
                            executorIdsByTaskId.getOrDefault(task.getId(), List.of()).stream()
                                    .map(executorUserNames::get)
                                    .filter(name -> name != null && !name.isBlank())
                                    .toList(),
                            task.getReporterId(),
                            userNames.get(task.getReporterId()),
                            task.getPriority(),
                            task.getStatus(),
                            task.getStartTime(),
                            task.getDueTime(),
                            task.getEstimateHours(),
                            task.getActualHours(),
                            task.getSortNo(),
                            tags.getOrDefault(task.getId(), List.of()),
                            task.getCreatedAt(),
                            task.getUpdatedAt()
                    );
                })
                .toList();
    }

    private List<TaskCommentItem> listComments(Long taskId) {
        return toCommentItems(commentMapper.selectList(new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getDeleted, 0)
                .eq(TaskComment::getTaskId, taskId)
                .orderByDesc(TaskComment::getId)));
    }

    private List<TaskWorklogItem> listWorklogs(Long taskId) {
        return toWorklogItems(worklogMapper.selectList(new LambdaQueryWrapper<TaskWorklog>()
                .eq(TaskWorklog::getDeleted, 0)
                .eq(TaskWorklog::getTaskId, taskId)
                .orderByDesc(TaskWorklog::getId)));
    }

    private List<TaskAttachmentItem> listAttachments(Long taskId) {
        return toAttachmentItems(attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>()
                .eq(TaskAttachment::getTaskId, taskId)
                .orderByDesc(TaskAttachment::getId)));
    }

    private List<TaskTagItem> listTags(Long taskId) {
        return tagMapper.selectList(new LambdaQueryWrapper<TaskTag>()
                        .eq(TaskTag::getTaskId, taskId)
                        .orderByDesc(TaskTag::getId))
                .stream()
                .map(this::toTagItem)
                .toList();
    }

    private List<TaskCommentItem> toCommentItems(List<TaskComment> comments) {
        Map<Long, String> userNames = loadUserNames(comments.stream().map(TaskComment::getUserId).toList());
        Map<Long, SysUser> users = loadUsers(comments.stream().map(TaskComment::getUserId).toList());
        return comments.stream()
                .map(comment -> {
                    SysUser user = users.get(comment.getUserId());
                    return new TaskCommentItem(
                            comment.getId(),
                            comment.getTaskId(),
                            comment.getUserId(),
                            user == null ? null : user.getUsername(),
                            userNames.get(comment.getUserId()),
                            comment.getContent(),
                            comment.getCreatedAt()
                    );
                })
                .toList();
    }

    private List<TaskWorklogItem> toWorklogItems(List<TaskWorklog> worklogs) {
        Map<Long, String> userNames = loadUserNames(worklogs.stream().map(TaskWorklog::getUserId).toList());
        Map<Long, SysUser> users = loadUsers(worklogs.stream().map(TaskWorklog::getUserId).toList());
        return worklogs.stream()
                .map(worklog -> {
                    SysUser user = users.get(worklog.getUserId());
                    return new TaskWorklogItem(
                            worklog.getId(),
                            worklog.getTaskId(),
                            worklog.getUserId(),
                            user == null ? null : user.getUsername(),
                            userNames.get(worklog.getUserId()),
                            worklog.getWorkDate(),
                            worklog.getHours(),
                            worklog.getDescription(),
                            worklog.getCreatedAt()
                    );
                })
                .toList();
    }

    private List<TaskAttachmentItem> toAttachmentItems(List<TaskAttachment> attachments) {
        Map<Long, String> userNames = loadUserNames(attachments.stream().map(TaskAttachment::getCreatedBy).toList());
        List<Long> fileIds = attachments.stream()
                .map(TaskAttachment::getFileId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, FileInfo> files = fileIds.isEmpty()
                ? Map.of()
                : fileInfoMapper.selectBatchIds(fileIds)
                        .stream()
                        .filter(file -> file != null && !Integer.valueOf(1).equals(file.getDeleted()))
                        .collect(Collectors.toMap(FileInfo::getId, Function.identity()));
        return attachments.stream()
                .map(attachment -> {
                    FileInfo file = files.get(attachment.getFileId());
                    return new TaskAttachmentItem(
                            attachment.getId(),
                            attachment.getTaskId(),
                            attachment.getFileId(),
                            file == null ? null : file.getOriginalName(),
                            file == null ? null : file.getContentType(),
                            file == null ? null : file.getFileSize(),
                            file == null ? null : file.getFileExt(),
                            attachment.getCreatedBy(),
                            userNames.get(attachment.getCreatedBy()),
                            attachment.getCreatedAt()
                    );
                })
                .toList();
    }

    private void ensureFileExists(Long fileId) {
        FileInfo file = fileInfoMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException("文件不存在");
        }
    }

    private TaskTag saveTag(Long taskId, String tagName, String tagColor) {
        TaskTag tag = new TaskTag();
        tag.setTaskId(taskId);
        tag.setTagName(tagName);
        tag.setTagColor(tagColor == null || tagColor.isBlank() ? "#2563EB" : tagColor);
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        return tag;
    }

    private TaskTagItem toTagItem(TaskTag tag) {
        return new TaskTagItem(tag.getId(), tag.getTaskId(), tag.getTagName(), tag.getTagColor(), tag.getCreatedAt());
    }

    private void refreshActualHours(Long taskId) {
        BigDecimal total = worklogMapper.selectList(new LambdaQueryWrapper<TaskWorklog>()
                        .eq(TaskWorklog::getDeleted, 0)
                        .eq(TaskWorklog::getTaskId, taskId))
                .stream()
                .map(worklog -> worklog.getHours() == null ? BigDecimal.ZERO : worklog.getHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Task task = getTaskEntity(taskId);
        task.setActualHours(total);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private Task getTaskEntity(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private Project getProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null || project.getDeleted() == 1) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    private void ensureUserExists(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("用户不存在");
        }
    }

    private List<Long> normalizeExecutorIds(List<Long> executorIds, Long defaultExecutorId) {
        List<Long> ids = executorIds == null ? List.of() : executorIds;
        List<Long> normalized = ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalized.isEmpty() && defaultExecutorId != null) {
            normalized = List.of(defaultExecutorId);
        }
        normalized.forEach(this::ensureUserExists);
        return normalized;
    }

    private void saveExecutors(Long taskId, List<Long> executorIds) {
        executorMapper.delete(new LambdaQueryWrapper<TaskExecutor>().eq(TaskExecutor::getTaskId, taskId));
        for (Long executorId : executorIds.stream().distinct().toList()) {
            TaskExecutor executor = new TaskExecutor();
            executor.setTaskId(taskId);
            executor.setUserId(executorId);
            executor.setCreatedAt(LocalDateTime.now());
            executorMapper.insert(executor);
        }
    }

    private List<Long> listExecutorIds(Long taskId) {
        return executorMapper.selectList(new LambdaQueryWrapper<TaskExecutor>()
                        .eq(TaskExecutor::getTaskId, taskId)
                        .orderByAsc(TaskExecutor::getId))
                .stream()
                .map(TaskExecutor::getUserId)
                .toList();
    }

    private List<TaskExecutorItem> listExecutors(Long taskId) {
        List<TaskExecutor> executors = executorMapper.selectList(new LambdaQueryWrapper<TaskExecutor>()
                .eq(TaskExecutor::getTaskId, taskId)
                .orderByAsc(TaskExecutor::getId));
        Map<Long, SysUser> users = loadUsers(executors.stream().map(TaskExecutor::getUserId).toList());
        return executors.stream()
                .map(executor -> {
                    SysUser user = users.get(executor.getUserId());
                    String nickname = user == null ? null : user.getNickname();
                    String username = user == null ? null : user.getUsername();
                    String displayName = nickname == null || nickname.isBlank() ? username : nickname;
                    return new TaskExecutorItem(
                            executor.getId(),
                            executor.getTaskId(),
                            executor.getUserId(),
                            username,
                            nickname,
                            displayName,
                            executor.getCreatedAt()
                    );
                })
                .toList();
    }

    private Map<Long, SysUser> loadUsers(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private Map<Long, String> loadUserNames(List<Long> userIds) {
        return loadUsers(userIds).values().stream()
                .collect(Collectors.toMap(SysUser::getId, user -> {
                    String nickname = user.getNickname();
                    return nickname == null || nickname.isBlank() ? user.getUsername() : nickname;
                }));
    }

    private void notifyTaskOwnerChanged(Task task, Long targetUserId, Long senderId, String title) {
        if (targetUserId == null || targetUserId.equals(senderId)) {
            return;
        }
        createNotification(
                task,
                targetUserId,
                senderId,
                title,
                "任务「" + task.getTitle() + "」(" + task.getTaskNo() + ") 需要你跟进。",
                "TASK"
        );
    }

    private void notifyTaskExecutorsAdded(Task task, List<Long> targetUserIds, Long senderId) {
        targetUserIds.stream()
                .filter(targetUserId -> targetUserId != null && !targetUserId.equals(senderId))
                .distinct()
                .forEach(targetUserId -> createNotification(
                        task,
                        targetUserId,
                        senderId,
                        "你被分配为任务执行人",
                        "任务「" + task.getTitle() + "」(" + task.getTaskNo() + ") 需要你参与执行。",
                        "TASK"
                ));
    }

    private void notifyTaskCommented(Task task, Long senderId) {
        Set<Long> targetUserIds = new LinkedHashSet<>();
        if (task.getAssigneeId() != null) {
            targetUserIds.add(task.getAssigneeId());
        }
        if (task.getReporterId() != null) {
            targetUserIds.add(task.getReporterId());
        }
        targetUserIds.addAll(listExecutorIds(task.getId()));
        targetUserIds.remove(senderId);
        targetUserIds.forEach(targetUserId -> createNotification(
                task,
                targetUserId,
                senderId,
                "任务有新评论",
                "任务「" + task.getTitle() + "」(" + task.getTaskNo() + ") 收到一条新评论。",
                "COMMENT"
        ));
    }

    private void createNotification(Task task, Long targetUserId, Long senderId, String title, String content, String notifyType) {
        try {
            notificationService.create(new NotificationRequest(
                    title,
                    content,
                    notifyType,
                    "USER",
                    targetUserId,
                    "TASK",
                    task.getId(),
                    taskBizTime(task)
            ), senderId);
        } catch (Exception ex) {
            // 通知属于辅助链路，不能影响任务创建、编辑或评论主流程，失败仅记录。
            log.debug("发送任务通知失败（忽略）targetUserId={} type={} 原因={}", targetUserId, notifyType, ex.getMessage());
        }
    }

    private LocalDateTime taskBizTime(Task task) {
        if (task.getDueTime() != null) {
            return task.getDueTime();
        }
        if (task.getUpdatedAt() != null) {
            return task.getUpdatedAt();
        }
        return task.getCreatedAt();
    }

    private String statusTitle(String status) {
        return switch (status) {
            case "TODO" -> "待处理";
            case "DOING" -> "进行中";
            case "TESTING" -> "测试中";
            case "DONE" -> "已完成";
            default -> status;
        };
    }

    private BigDecimal progressByStatus(String status) {
        return switch (status) {
            case "TODO" -> BigDecimal.ZERO;
            case "DOING" -> BigDecimal.valueOf(45);
            case "TESTING" -> BigDecimal.valueOf(75);
            case "DONE", "CLOSED" -> BigDecimal.valueOf(100);
            default -> BigDecimal.ZERO;
        };
    }
}

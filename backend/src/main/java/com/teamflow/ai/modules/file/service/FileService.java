package com.teamflow.ai.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.file.dto.FileContent;
import com.teamflow.ai.modules.file.dto.FileItem;
import com.teamflow.ai.modules.file.dto.FileShareItem;
import com.teamflow.ai.modules.file.dto.FileShareRequest;
import com.teamflow.ai.modules.file.dto.FileUpdateRequest;
import com.teamflow.ai.modules.file.entity.FileInfo;
import com.teamflow.ai.modules.file.entity.FileShare;
import com.teamflow.ai.modules.file.mapper.FileInfoMapper;
import com.teamflow.ai.modules.file.mapper.FileShareMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final String LOCAL_BUCKET = "local";

    private final FileInfoMapper fileInfoMapper;
    private final FileShareMapper fileShareMapper;
    private final SysUserMapper userMapper;
    private final FileStorageProperties storageProperties;
    private final Path localRoot = Path.of("logs", "uploads").toAbsolutePath();

    private volatile MinioClient minioClient;

    public FileService(
            FileInfoMapper fileInfoMapper,
            FileShareMapper fileShareMapper,
            SysUserMapper userMapper,
            FileStorageProperties storageProperties
    ) {
        this.fileInfoMapper = fileInfoMapper;
        this.fileShareMapper = fileShareMapper;
        this.userMapper = userMapper;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public FileItem upload(MultipartFile multipartFile, String bizType, Long bizId, Long uploaderId) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = sanitizeFileName(multipartFile.getOriginalFilename());
        String ext = extractExt(originalName);
        String contentType = multipartFile.getContentType() == null || multipartFile.getContentType().isBlank()
                ? "application/octet-stream"
                : multipartFile.getContentType();
        String objectKey = buildObjectKey(bizType, ext);

        String bucket = tryUploadToMinio(multipartFile, objectKey, contentType);
        if (bucket == null) {
            bucket = LOCAL_BUCKET;
            uploadToLocal(multipartFile, objectKey);
        }
        log.info("文件上传成功 originalName={} size={} objectKey={} bucket={} uploaderId={}",
                originalName, multipartFile.getSize(), objectKey, bucket, uploaderId);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setBizType(defaultBizType(bizType));
        fileInfo.setBizId(bizId);
        fileInfo.setBucketName(bucket);
        fileInfo.setObjectKey(objectKey);
        fileInfo.setOriginalName(originalName);
        fileInfo.setContentType(contentType);
        fileInfo.setFileSize(multipartFile.getSize());
        fileInfo.setFileExt(ext);
        fileInfo.setUploaderId(uploaderId);
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setDeleted(0);
        fileInfoMapper.insert(fileInfo);
        return toFileItems(List.of(fileInfo)).get(0);
    }

    @Transactional
    public FileItem createMetadata(FileUpdateRequest request, Long uploaderId) {
        if (request == null || request.originalName() == null || request.originalName().isBlank()) {
            throw new BusinessException("文件名不能为空");
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setBizType(defaultBizType(request.bizType()));
        fileInfo.setBizId(request.bizId());
        fileInfo.setBucketName(LOCAL_BUCKET);
        fileInfo.setObjectKey("metadata/" + UUID.randomUUID());
        fileInfo.setOriginalName(sanitizeFileName(request.originalName()));
        fileInfo.setContentType("application/octet-stream");
        fileInfo.setFileSize(0L);
        fileInfo.setFileExt(extractExt(fileInfo.getOriginalName()));
        fileInfo.setUploaderId(uploaderId);
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setDeleted(0);
        fileInfoMapper.insert(fileInfo);
        return toFileItems(List.of(fileInfo)).get(0);
    }

    public PageResult<FileItem> pageFiles(long page, long size, String keyword, String bizType, Long bizId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getDeleted, 0)
                .eq(bizType != null && !bizType.isBlank(), FileInfo::getBizType, bizType)
                .eq(bizId != null, FileInfo::getBizId, bizId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(FileInfo::getOriginalName, keyword)
                        .or()
                        .like(FileInfo::getFileExt, keyword)
                        .or()
                        .like(FileInfo::getBizType, keyword))
                .orderByDesc(FileInfo::getId);
        Page<FileInfo> result = fileInfoMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toFileItems(result.getRecords()));
    }

    public FileItem getFile(Long id) {
        return toFileItems(List.of(getFileEntity(id))).get(0);
    }

    @Transactional
    public FileItem updateFile(Long id, FileUpdateRequest request) {
        FileInfo fileInfo = getFileEntity(id);
        if (request.bizType() != null) {
            fileInfo.setBizType(defaultBizType(request.bizType()));
        }
        if (request.bizId() != null) {
            fileInfo.setBizId(request.bizId());
        }
        if (request.originalName() != null && !request.originalName().isBlank()) {
            fileInfo.setOriginalName(sanitizeFileName(request.originalName()));
            fileInfo.setFileExt(extractExt(fileInfo.getOriginalName()));
        }
        fileInfoMapper.updateById(fileInfo);
        log.info("更新文件元数据 fileId={} originalName={} bizType={} bizId={}",
                fileInfo.getId(), fileInfo.getOriginalName(), fileInfo.getBizType(), fileInfo.getBizId());
        return getFile(fileInfo.getId());
    }

    @Transactional
    public void deleteFile(Long id) {
        FileInfo fileInfo = getFileEntity(id);
        fileInfoMapper.deleteById(id);
        fileShareMapper.delete(new LambdaQueryWrapper<FileShare>().eq(FileShare::getFileId, id));
        deleteObjectQuietly(fileInfo);
        log.info("删除文件（含分享记录）fileId={} originalName={} objectKey={}",
                id, fileInfo.getOriginalName(), fileInfo.getObjectKey());
    }

    @Transactional
    public void batchDeleteFiles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> validIds = ids.stream().distinct().toList();
        log.info("批量删除文件 fileIds={}", validIds);
        validIds.forEach(this::deleteFile);
    }

    public FileContent loadContent(Long id) {
        FileInfo fileInfo = getFileEntity(id);
        try {
            Resource resource;
            long contentLength = fileInfo.getFileSize() == null ? -1 : fileInfo.getFileSize();
            if (LOCAL_BUCKET.equals(fileInfo.getBucketName())) {
                Path path = localPath(fileInfo.getObjectKey());
                if (!Files.exists(path)) {
                    throw new BusinessException("本地文件不存在");
                }
                resource = new InputStreamResource(Files.newInputStream(path));
                contentLength = Files.size(path);
            } else {
                InputStream inputStream = getMinioClient().getObject(GetObjectArgs.builder()
                        .bucket(fileInfo.getBucketName())
                        .object(fileInfo.getObjectKey())
                        .build());
                resource = new InputStreamResource(inputStream);
            }
            return new FileContent(resource, fileInfo.getOriginalName(), fileInfo.getContentType(), contentLength);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // 记录真实异常（如 MinIO 鉴权失败 / 对象不存在），否则只剩通用提示难以排查
            log.error("读取文件失败 id={} bucket={} objectKey={}",
                    id, fileInfo.getBucketName(), fileInfo.getObjectKey(), exception);
            throw new BusinessException("读取文件失败");
        }
    }

    @Transactional
    public FileShareItem createShare(FileShareRequest request, Long currentUserId) {
        FileInfo fileInfo = getFileEntity(request.fileId());
        // 同一文件复用未过期的分享码：避免每次分享都生成新链接，方便对外统一传播与回收
        FileShare existing = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getFileId, fileInfo.getId())
                .eq(FileShare::getDeleted, 0)
                .gt(FileShare::getExpireTime, LocalDateTime.now())
                .orderByDesc(FileShare::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return toShareItems(List.of(existing)).get(0);
        }
        FileShare share = new FileShare();
        share.setFileId(fileInfo.getId());
        share.setShareCode(UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        int expireDays = request.expireDays() == null || request.expireDays() <= 0 ? 7 : request.expireDays();
        share.setExpireTime(LocalDateTime.now().plusDays(expireDays));
        share.setCreatedBy(currentUserId);
        share.setCreatedAt(LocalDateTime.now());
        share.setDeleted(0);
        fileShareMapper.insert(share);
        log.info("创建文件分享 fileId={} shareCode={} expireDays={} createdBy={}",
                fileInfo.getId(), share.getShareCode(), expireDays, currentUserId);
        return toShareItems(List.of(share)).get(0);
    }

    public FileShareItem getShareByCode(String shareCode) {
        return toShareItems(List.of(findActiveShareByCode(shareCode))).get(0);
    }

    public FileContent loadShareContent(String shareCode) {
        return loadContent(findActiveShareByCode(shareCode).getFileId());
    }

    private FileShare findActiveShareByCode(String shareCode) {
        if (shareCode == null || shareCode.isBlank()) {
            throw new BusinessException("分享码无效");
        }
        FileShare share = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getShareCode, shareCode.trim().toUpperCase(Locale.ROOT))
                .eq(FileShare::getDeleted, 0)
                .last("LIMIT 1"));
        if (share == null) {
            throw new BusinessException("分享链接不存在或已被取消");
        }
        if (share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("分享链接已过期");
        }
        return share;
    }

    public PageResult<FileShareItem> pageShares(long page, long size, String keyword) {
        LambdaQueryWrapper<FileShare> wrapper = new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getDeleted, 0)
                .like(keyword != null && !keyword.isBlank(), FileShare::getShareCode, keyword)
                .orderByDesc(FileShare::getId);
        Page<FileShare> result = fileShareMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toShareItems(result.getRecords()));
    }

    public FileShareItem getShare(Long id) {
        FileShare share = fileShareMapper.selectById(id);
        if (share == null || share.getDeleted() == 1) {
            throw new BusinessException("文件分享不存在");
        }
        return toShareItems(List.of(share)).get(0);
    }

    @Transactional
    public FileShareItem updateShare(Long id, FileShareRequest request, Long currentUserId) {
        FileShare share = fileShareMapper.selectById(id);
        if (share == null || share.getDeleted() == 1) {
            throw new BusinessException("文件分享不存在");
        }
        getFileEntity(request.fileId());
        share.setFileId(request.fileId());
        int expireDays = request.expireDays() == null || request.expireDays() <= 0 ? 7 : request.expireDays();
        share.setExpireTime(LocalDateTime.now().plusDays(expireDays));
        share.setCreatedBy(currentUserId);
        fileShareMapper.updateById(share);
        log.info("更新文件分享 shareId={} fileId={} expireDays={} 操作人={}",
                share.getId(), request.fileId(), expireDays, currentUserId);
        return getShare(share.getId());
    }

    @Transactional
    public void deleteShare(Long id) {
        fileShareMapper.deleteById(id);
        log.info("取消文件分享 shareId={}", id);
    }

    @Transactional
    public void batchDeleteShares(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        fileShareMapper.deleteBatchIds(ids);
        log.info("批量取消文件分享 shareIds={}", ids);
    }

    private String tryUploadToMinio(MultipartFile multipartFile, String objectKey, String contentType) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            ensureBucket();
            getMinioClient().putObject(PutObjectArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, multipartFile.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return storageProperties.getBucket();
        } catch (Exception ex) {
            // MinIO 不可用时回退本地磁盘存储，记录原因便于排查（例如端点不通、凭据错误、bucket 权限）
            log.warn("MinIO 上传失败，回退本地存储 objectKey={} 原因={}", objectKey, ex.getMessage());
            return null;
        }
    }

    private void uploadToLocal(MultipartFile multipartFile, String objectKey) {
        try {
            Path target = localPath(objectKey);
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = multipartFile.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException("本地文件保存失败");
        }
    }

    private void ensureBucket() throws Exception {
        String bucket = storageProperties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("MinIO bucket 未配置");
        }
        boolean exists = getMinioClient().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            getMinioClient().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private MinioClient getMinioClient() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(storageProperties.getEndpoint())
                            .credentials(storageProperties.getAccessKey(), storageProperties.getSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private void deleteObjectQuietly(FileInfo fileInfo) {
        try {
            if (LOCAL_BUCKET.equals(fileInfo.getBucketName())) {
                Files.deleteIfExists(localPath(fileInfo.getObjectKey()));
                return;
            }
            getMinioClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(fileInfo.getBucketName())
                    .object(fileInfo.getObjectKey())
                    .build());
        } catch (Exception ex) {
            // 元数据删除对产品 UI 是权威的，存储清理为尽力而为，失败仅记录不影响主流程
            log.debug("删除存储对象失败（忽略）bucket={} objectKey={} 原因={}",
                    fileInfo.getBucketName(), fileInfo.getObjectKey(), ex.getMessage());
        }
    }

    private FileInfo getFileEntity(Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        if (fileInfo == null || fileInfo.getDeleted() == 1) {
            throw new BusinessException("文件不存在");
        }
        return fileInfo;
    }

    private List<FileItem> toFileItems(List<FileInfo> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        Map<Long, String> uploaderNames = loadUserNames(files.stream().map(FileInfo::getUploaderId).toList());
        return files.stream()
                .map(file -> new FileItem(
                        file.getId(),
                        file.getBizType(),
                        file.getBizId(),
                        file.getBucketName(),
                        file.getObjectKey(),
                        file.getOriginalName(),
                        file.getContentType(),
                        file.getFileSize(),
                        file.getFileExt(),
                        file.getUploaderId(),
                        uploaderNames.get(file.getUploaderId()),
                        file.getCreatedAt()
                ))
                .toList();
    }

    private List<FileShareItem> toShareItems(List<FileShare> shares) {
        if (shares == null || shares.isEmpty()) {
            return List.of();
        }
        Map<Long, FileInfo> files = fileInfoMapper.selectBatchIds(shares.stream().map(FileShare::getFileId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(FileInfo::getId, Function.identity()));
        Map<Long, String> creatorNames = loadUserNames(shares.stream().map(FileShare::getCreatedBy).toList());
        return shares.stream()
                .map(share -> {
                    FileInfo file = files.get(share.getFileId());
                    return new FileShareItem(
                            share.getId(),
                            share.getFileId(),
                            file == null ? null : file.getOriginalName(),
                            share.getShareCode(),
                            share.getExpireTime(),
                            share.getCreatedBy(),
                            creatorNames.get(share.getCreatedBy()),
                            share.getCreatedAt()
                    );
                })
                .toList();
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

    private String defaultBizType(String bizType) {
        return bizType == null || bizType.isBlank() ? "COMMON" : bizType.trim().toUpperCase(Locale.ROOT);
    }

    private String sanitizeFileName(String originalName) {
        String fileName = originalName == null || originalName.isBlank() ? "unnamed" : originalName.trim();
        return fileName.replace("\\", "_").replace("/", "_");
    }

    private String extractExt(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String buildObjectKey(String bizType, String ext) {
        LocalDate today = LocalDate.now();
        String suffix = ext == null || ext.isBlank() ? "" : "." + ext;
        return "%s/%s/%02d/%02d/%s%s".formatted(
                defaultBizType(bizType).toLowerCase(Locale.ROOT),
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                suffix
        );
    }

    private Path localPath(String objectKey) {
        Path target = localRoot.resolve(objectKey).normalize();
        if (!target.startsWith(localRoot)) {
            throw new BusinessException("非法文件路径");
        }
        return target;
    }

    public FileItem ensureLocalDemoFile(Long uploaderId) {
        FileInfo existing = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getDeleted, 0)
                .eq(FileInfo::getOriginalName, "TeamFlow 文件中心说明.md")
                .last("LIMIT 1"));
        if (existing != null) {
            return toFileItems(List.of(existing)).get(0);
        }
        String content = "## TeamFlow 文件中心\n\n支持上传、预览、下载、业务归档和分享码演示。";
        String objectKey = "demo/teamflow-file-center.md";
        try {
            Files.createDirectories(localPath(objectKey).getParent());
            Files.copy(new ByteArrayInputStream(content.getBytes()), localPath(objectKey), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException("初始化文件演示数据失败");
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setBizType("COMMON");
        fileInfo.setBizId(0L);
        fileInfo.setBucketName(LOCAL_BUCKET);
        fileInfo.setObjectKey(objectKey);
        fileInfo.setOriginalName("TeamFlow 文件中心说明.md");
        fileInfo.setContentType("text/markdown");
        fileInfo.setFileSize((long) content.getBytes().length);
        fileInfo.setFileExt("md");
        fileInfo.setUploaderId(uploaderId);
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setDeleted(0);
        fileInfoMapper.insert(fileInfo);
        return toFileItems(List.of(fileInfo)).get(0);
    }
}

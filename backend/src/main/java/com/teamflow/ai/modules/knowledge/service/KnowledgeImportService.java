package com.teamflow.ai.modules.knowledge.service;

import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.file.dto.FileItem;
import com.teamflow.ai.modules.file.dto.FileUpdateRequest;
import com.teamflow.ai.modules.file.service.FileService;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocRequest;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeImportResult;
import com.teamflow.ai.modules.knowledge.dto.KnowledgePublishRequest;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeImportService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeImportService.class);

    private final FileService fileService;
    private final KnowledgeService knowledgeService;
    private final Tika tika = new Tika();

    public KnowledgeImportService(FileService fileService, KnowledgeService knowledgeService) {
        this.fileService = fileService;
        this.knowledgeService = knowledgeService;
    }

    @Transactional
    public KnowledgeImportResult importFile(
            MultipartFile file,
            Long spaceId,
            Long parentId,
            String title,
            List<String> tags,
            boolean autoPublish,
            Long currentUserId
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文档不能为空");
        }
        if (spaceId == null) {
            throw new BusinessException("请选择知识空间");
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        String docTitle = title == null || title.isBlank() ? stripExtension(originalName) : title.trim();
        log.info("开始导入知识文档 fileName={} spaceId={} autoPublish={} 操作人={}",
                originalName, spaceId, autoPublish, currentUserId);
        ExtractedDocument extracted = extractDocument(file, originalName, docTitle);
        FileItem uploadedFile = fileService.upload(file, "KNOWLEDGE", 0L, currentUserId);

        KnowledgeDocItem doc = knowledgeService.createDoc(new KnowledgeDocRequest(
                spaceId,
                parentId,
                docTitle,
                extracted.markdown(),
                autoPublish ? "DRAFT" : "DRAFT",
                100,
                tags
        ), currentUserId);
        FileItem linkedFile = fileService.updateFile(uploadedFile.id(), new FileUpdateRequest("KNOWLEDGE", doc.id(), uploadedFile.originalName()));
        boolean indexed = false;
        if (autoPublish) {
            doc = knowledgeService.publish(doc.id(), new KnowledgePublishRequest("从文件「" + originalName + "」导入并自动发布"), currentUserId);
            indexed = true;
        }
        log.info("知识文档导入完成 docId={} fileId={} 解析方式={} autoPublish={} 已建索引={}",
                doc.id(), linkedFile.id(), extracted.mode(), autoPublish, indexed);
        return new KnowledgeImportResult(doc, linkedFile, autoPublish, indexed, extracted.mode());
    }

    private ExtractedDocument extractDocument(MultipartFile file, String originalName, String title) {
        String ext = extension(originalName);
        try {
            if (List.of("md", "markdown", "txt", "text", "json", "xml", "sql", "csv").contains(ext)) {
                String text = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
                if (text.isBlank()) {
                    throw new BusinessException("文档内容为空，无法导入知识库");
                }
                return new ExtractedDocument(toMarkdown(title, text, ext), "plain-text");
            }
            String text = tika.parseToString(file.getInputStream()).trim();
            if (text.isBlank()) {
                throw new BusinessException("暂未从该文件中解析到可检索文本");
            }
            return new ExtractedDocument(toMarkdown(title, text, ext), "apache-tika");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // 解析异常对用户统一返回友好提示，原始原因记 warn 便于排查（如不支持的格式、损坏文件）
            log.warn("文档解析失败 fileName={} ext={} 原因={}", originalName, ext, exception.getMessage());
            throw new BusinessException("文档解析失败，请上传 md、txt、pdf 或 docx 文件");
        }
    }

    private String toMarkdown(String title, String text, String ext) {
        if ("md".equals(ext) || "markdown".equals(ext)) {
            return text;
        }
        return "# " + title + "\n\n" + text.replaceAll("\\r\\n?", "\n").trim();
    }

    private String sanitizeFileName(String originalName) {
        String fileName = originalName == null || originalName.isBlank() ? "unnamed" : originalName.trim();
        return fileName.replace("\\", "_").replace("/", "_");
    }

    private String stripExtension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private record ExtractedDocument(String markdown, String mode) {
    }
}

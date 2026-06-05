package com.teamflow.ai.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.modules.ai.service.AiKnowledgeIndexService;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocRequest;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeDocTreeNode;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeFavoriteItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeFavoriteRequest;
import com.teamflow.ai.modules.knowledge.dto.KnowledgePublishRequest;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeSpaceItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeSpaceRequest;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeTagItem;
import com.teamflow.ai.modules.knowledge.dto.KnowledgeVersionItem;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeDoc;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeDocVersion;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeFavorite;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeSpace;
import com.teamflow.ai.modules.knowledge.entity.KnowledgeTag;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeDocMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeDocVersionMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeFavoriteMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeSpaceMapper;
import com.teamflow.ai.modules.knowledge.mapper.KnowledgeTagMapper;
import com.teamflow.ai.modules.team.entity.Team;
import com.teamflow.ai.modules.team.mapper.TeamMapper;
import com.teamflow.ai.modules.user.entity.SysUser;
import com.teamflow.ai.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeDocVersionMapper versionMapper;
    private final KnowledgeTagMapper tagMapper;
    private final KnowledgeFavoriteMapper favoriteMapper;
    private final TeamMapper teamMapper;
    private final SysUserMapper userMapper;
    private final AiKnowledgeIndexService knowledgeIndexService;

    public KnowledgeService(
            KnowledgeSpaceMapper spaceMapper,
            KnowledgeDocMapper docMapper,
            KnowledgeDocVersionMapper versionMapper,
            KnowledgeTagMapper tagMapper,
            KnowledgeFavoriteMapper favoriteMapper,
            TeamMapper teamMapper,
            SysUserMapper userMapper,
            AiKnowledgeIndexService knowledgeIndexService
    ) {
        this.spaceMapper = spaceMapper;
        this.docMapper = docMapper;
        this.versionMapper = versionMapper;
        this.tagMapper = tagMapper;
        this.favoriteMapper = favoriteMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    public PageResult<KnowledgeSpaceItem> pageSpaces(long page, long size, String keyword) {
        LambdaQueryWrapper<KnowledgeSpace> wrapper = new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(KnowledgeSpace::getSpaceName, keyword)
                        .or()
                        .like(KnowledgeSpace::getDescription, keyword))
                .orderByDesc(KnowledgeSpace::getId);
        Page<KnowledgeSpace> result = spaceMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toSpaceItems(result.getRecords()));
    }

    @Transactional
    public KnowledgeSpaceItem createSpace(KnowledgeSpaceRequest request, Long currentUserId) {
        KnowledgeSpace space = new KnowledgeSpace();
        fillSpace(space, request, currentUserId);
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());
        space.setDeleted(0);
        spaceMapper.insert(space);
        return toSpaceItems(List.of(space)).get(0);
    }

    public KnowledgeSpaceItem getSpace(Long id) {
        return toSpaceItems(List.of(getSpaceEntity(id))).get(0);
    }

    @Transactional
    public KnowledgeSpaceItem updateSpace(Long id, KnowledgeSpaceRequest request, Long currentUserId) {
        KnowledgeSpace space = getSpaceEntity(id);
        fillSpace(space, request, currentUserId);
        space.setUpdatedAt(LocalDateTime.now());
        spaceMapper.updateById(space);
        return getSpace(space.getId());
    }

    @Transactional
    public void deleteSpace(Long id) {
        getSpaceEntity(id);
        List<Long> docIds = docMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getSpaceId, id))
                .stream()
                .map(KnowledgeDoc::getId)
                .toList();
        spaceMapper.deleteById(id);
        for (Long docId : docIds) {
            docMapper.deleteById(docId);
            tagMapper.delete(new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getDocId, docId));
            favoriteMapper.delete(new LambdaQueryWrapper<KnowledgeFavorite>().eq(KnowledgeFavorite::getDocId, docId));
            knowledgeIndexService.deleteDocumentIndex(docId);
        }
    }

    public PageResult<KnowledgeDocItem> pageDocs(long page, long size, Long spaceId, String keyword, Long currentUserId) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = baseDocWrapper(spaceId, keyword)
                .orderByAsc(KnowledgeDoc::getParentId)
                .orderByAsc(KnowledgeDoc::getSortNo)
                .orderByDesc(KnowledgeDoc::getId);
        Page<KnowledgeDoc> result = docMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toDocItems(result.getRecords(), currentUserId));
    }

    public List<KnowledgeDocTreeNode> tree(Long spaceId, String keyword) {
        List<KnowledgeDoc> docs = docMapper.selectList(baseDocWrapper(spaceId, keyword)
                        .orderByAsc(KnowledgeDoc::getParentId)
                        .orderByAsc(KnowledgeDoc::getSortNo))
                .stream()
                .sorted(Comparator.comparing(doc -> doc.getSortNo() == null ? 0 : doc.getSortNo()))
                .toList();
        Map<Long, List<KnowledgeDoc>> children = docs.stream()
                .collect(Collectors.groupingBy(doc -> doc.getParentId() == null ? 0L : doc.getParentId()));
        return children.getOrDefault(0L, List.of()).stream()
                .map(doc -> toTreeNode(doc, children))
                .toList();
    }

    @Transactional
    public KnowledgeDocItem createDoc(KnowledgeDocRequest request, Long currentUserId) {
        getSpaceEntity(request.spaceId());
        KnowledgeDoc doc = new KnowledgeDoc();
        fillDoc(doc, request, currentUserId);
        doc.setAuthorId(currentUserId);
        doc.setVersionNo(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc.setDeleted(0);
        docMapper.insert(doc);
        replaceTags(doc.getId(), request.tags());
        return getDoc(doc.getId(), currentUserId);
    }

    public KnowledgeDocItem getDoc(Long id, Long currentUserId) {
        return toDocItems(List.of(getDocEntity(id)), currentUserId).get(0);
    }

    @Transactional
    public KnowledgeDocItem updateDoc(Long id, KnowledgeDocRequest request, Long currentUserId) {
        KnowledgeDoc doc = getDocEntity(id);
        getSpaceEntity(request.spaceId());
        fillDoc(doc, request, currentUserId);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        replaceTags(doc.getId(), request.tags());
        return getDoc(doc.getId(), currentUserId);
    }

    @Transactional
    public void deleteDoc(Long id) {
        getDocEntity(id);
        docMapper.deleteById(id);
        tagMapper.delete(new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getDocId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<KnowledgeFavorite>().eq(KnowledgeFavorite::getDocId, id));
        knowledgeIndexService.deleteDocumentIndex(id);
    }

    @Transactional
    public KnowledgeDocItem publish(Long id, KnowledgePublishRequest request, Long currentUserId) {
        KnowledgeDoc doc = getDocEntity(id);
        int nextVersion = (doc.getVersionNo() == null ? 0 : doc.getVersionNo()) + 1;
        KnowledgeDocVersion version = new KnowledgeDocVersion();
        version.setDocId(doc.getId());
        version.setVersionNo(nextVersion);
        version.setTitle(doc.getTitle());
        version.setContentMd(doc.getContentMd());
        version.setEditorId(currentUserId);
        version.setChangeSummary(request == null || request.changeSummary() == null || request.changeSummary().isBlank()
                ? "发布文档"
                : request.changeSummary());
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);

        doc.setDocStatus("PUBLISHED");
        doc.setVersionNo(nextVersion);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        knowledgeIndexService.rebuildDocumentIndex(doc);
        return getDoc(doc.getId(), currentUserId);
    }

    @Transactional
    public KnowledgeDocItem restore(Long id, Long versionId, Long currentUserId) {
        KnowledgeDoc doc = getDocEntity(id);
        KnowledgeDocVersion version = versionMapper.selectById(versionId);
        if (version == null || !doc.getId().equals(version.getDocId())) {
            throw new BusinessException("历史版本不存在");
        }
        doc.setTitle(version.getTitle());
        doc.setContentMd(version.getContentMd());
        doc.setContentText(markdownToText(version.getContentMd()));
        doc.setVersionNo(version.getVersionNo());
        doc.setDocStatus("PUBLISHED");
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        knowledgeIndexService.rebuildDocumentIndex(doc);
        return getDoc(doc.getId(), currentUserId);
    }

    public PageResult<KnowledgeVersionItem> pageVersions(long page, long size, Long docId, String keyword) {
        LambdaQueryWrapper<KnowledgeDocVersion> wrapper = new LambdaQueryWrapper<KnowledgeDocVersion>()
                .eq(docId != null, KnowledgeDocVersion::getDocId, docId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(KnowledgeDocVersion::getTitle, keyword)
                        .or()
                        .like(KnowledgeDocVersion::getChangeSummary, keyword))
                .orderByDesc(KnowledgeDocVersion::getVersionNo);
        Page<KnowledgeDocVersion> result = versionMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), toVersionItems(result.getRecords()));
    }

    public KnowledgeVersionItem getVersion(Long id) {
        KnowledgeDocVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException("历史版本不存在");
        }
        return toVersionItems(List.of(version)).get(0);
    }

    @Transactional
    public KnowledgeFavoriteItem createFavorite(KnowledgeFavoriteRequest request, Long currentUserId) {
        KnowledgeDoc doc = getDocEntity(request.docId());
        KnowledgeFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getDocId, request.docId())
                .eq(KnowledgeFavorite::getUserId, currentUserId)
                .last("LIMIT 1"));
        if (existing != null) {
            return toFavoriteItem(existing, doc);
        }
        KnowledgeFavorite favorite = new KnowledgeFavorite();
        favorite.setDocId(request.docId());
        favorite.setUserId(currentUserId);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);
        return toFavoriteItem(favorite, doc);
    }

    public PageResult<KnowledgeFavoriteItem> pageFavorites(long page, long size, Long currentUserId) {
        Page<KnowledgeFavorite> result = favoriteMapper.selectPage(PageRequestUtils.of(page, size), new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getUserId, currentUserId)
                .orderByDesc(KnowledgeFavorite::getId));
        return new PageResult<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(favorite -> toFavoriteItem(favorite, docMapper.selectById(favorite.getDocId())))
                        .toList()
        );
    }

    @Transactional
    public void deleteFavorite(Long id, Long currentUserId) {
        KnowledgeFavorite favorite = favoriteMapper.selectById(id);
        if (favorite != null && favorite.getUserId().equals(currentUserId)) {
            favoriteMapper.deleteById(id);
        }
    }

    @Transactional
    public KnowledgeTagItem createTag(Long docId, String tagName) {
        getDocEntity(docId);
        if (tagName == null || tagName.isBlank()) {
            throw new BusinessException("标签名称不能为空");
        }
        KnowledgeTag tag = new KnowledgeTag();
        tag.setDocId(docId);
        tag.setTagName(tagName.trim());
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        return toTagItem(tag);
    }

    public PageResult<KnowledgeTagItem> pageTags(long page, long size, Long docId, String keyword) {
        LambdaQueryWrapper<KnowledgeTag> wrapper = new LambdaQueryWrapper<KnowledgeTag>()
                .eq(docId != null, KnowledgeTag::getDocId, docId)
                .like(keyword != null && !keyword.isBlank(), KnowledgeTag::getTagName, keyword)
                .orderByDesc(KnowledgeTag::getId);
        Page<KnowledgeTag> result = tagMapper.selectPage(PageRequestUtils.of(page, size), wrapper);
        return new PageResult<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toTagItem).toList()
        );
    }

    @Transactional
    public void deleteTag(Long id) {
        tagMapper.deleteById(id);
    }

    private void fillSpace(KnowledgeSpace space, KnowledgeSpaceRequest request, Long currentUserId) {
        space.setTeamId(request.teamId() == null ? defaultTeamId() : request.teamId());
        space.setSpaceName(request.spaceName());
        space.setDescription(request.description());
        space.setVisibility(request.visibility() == null || request.visibility().isBlank() ? "TEAM" : request.visibility());
        if (space.getOwnerId() == null) {
            space.setOwnerId(currentUserId);
        }
    }

    private void fillDoc(KnowledgeDoc doc, KnowledgeDocRequest request, Long currentUserId) {
        String markdown = request.contentMd() == null ? "" : request.contentMd();
        doc.setSpaceId(request.spaceId());
        doc.setParentId(request.parentId() == null ? 0L : request.parentId());
        doc.setTitle(request.title());
        doc.setContentMd(markdown);
        doc.setContentText(markdownToText(markdown));
        doc.setDocStatus(request.docStatus() == null || request.docStatus().isBlank() ? "DRAFT" : request.docStatus());
        doc.setSortNo(request.sortNo() == null ? 100 : request.sortNo());
        if (doc.getAuthorId() == null) {
            doc.setAuthorId(currentUserId);
        }
    }

    private KnowledgeDocTreeNode toTreeNode(KnowledgeDoc doc, Map<Long, List<KnowledgeDoc>> children) {
        return new KnowledgeDocTreeNode(
                doc.getId(),
                doc.getParentId(),
                doc.getTitle(),
                doc.getDocStatus(),
                doc.getVersionNo(),
                listTags(doc.getId()),
                children.getOrDefault(doc.getId(), List.of()).stream()
                        .map(child -> toTreeNode(child, children))
                        .toList()
        );
    }

    private List<KnowledgeSpaceItem> toSpaceItems(List<KnowledgeSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return List.of();
        }
        Map<Long, String> ownerNames = loadUserNames(spaces.stream().map(KnowledgeSpace::getOwnerId).toList());
        Map<Long, Long> docCounts = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getDeleted, 0)
                        .in(KnowledgeDoc::getSpaceId, spaces.stream().map(KnowledgeSpace::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(KnowledgeDoc::getSpaceId, Collectors.counting()));
        return spaces.stream()
                .map(space -> new KnowledgeSpaceItem(
                        space.getId(),
                        space.getTeamId(),
                        space.getSpaceName(),
                        space.getDescription(),
                        space.getVisibility(),
                        space.getOwnerId(),
                        ownerNames.get(space.getOwnerId()),
                        docCounts.getOrDefault(space.getId(), 0L),
                        space.getCreatedAt()
                ))
                .toList();
    }

    private List<KnowledgeDocItem> toDocItems(List<KnowledgeDoc> docs, Long currentUserId) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeSpace> spaces = loadSpaces(docs.stream().map(KnowledgeDoc::getSpaceId).toList());
        Map<Long, String> authorNames = loadUserNames(docs.stream().map(KnowledgeDoc::getAuthorId).toList());
        Map<Long, KnowledgeFavorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<KnowledgeFavorite>()
                        .eq(KnowledgeFavorite::getUserId, currentUserId)
                        .in(KnowledgeFavorite::getDocId, docs.stream().map(KnowledgeDoc::getId).toList()))
                .stream()
                .collect(Collectors.toMap(KnowledgeFavorite::getDocId, Function.identity(), (left, right) -> left));
        return docs.stream()
                .map(doc -> {
                    KnowledgeSpace space = spaces.get(doc.getSpaceId());
                    KnowledgeFavorite favorite = favorites.get(doc.getId());
                    return new KnowledgeDocItem(
                            doc.getId(),
                            doc.getSpaceId(),
                            space == null ? null : space.getSpaceName(),
                            doc.getParentId(),
                            doc.getTitle(),
                            doc.getContentMd(),
                            doc.getContentText(),
                            doc.getAuthorId(),
                            authorNames.get(doc.getAuthorId()),
                            doc.getDocStatus(),
                            doc.getSortNo(),
                            doc.getVersionNo(),
                            favorite != null,
                            favorite == null ? null : favorite.getId(),
                            listTags(doc.getId()),
                            doc.getCreatedAt(),
                            doc.getUpdatedAt()
                    );
                })
                .toList();
    }

    private List<KnowledgeVersionItem> toVersionItems(List<KnowledgeDocVersion> versions) {
        Map<Long, String> editorNames = loadUserNames(versions.stream().map(KnowledgeDocVersion::getEditorId).toList());
        return versions.stream()
                .map(version -> new KnowledgeVersionItem(
                        version.getId(),
                        version.getDocId(),
                        version.getVersionNo(),
                        version.getTitle(),
                        version.getContentMd(),
                        version.getEditorId(),
                        editorNames.get(version.getEditorId()),
                        version.getChangeSummary(),
                        version.getCreatedAt()
                ))
                .toList();
    }

    private KnowledgeFavoriteItem toFavoriteItem(KnowledgeFavorite favorite, KnowledgeDoc doc) {
        return new KnowledgeFavoriteItem(
                favorite.getId(),
                favorite.getDocId(),
                doc == null ? null : doc.getTitle(),
                favorite.getUserId(),
                favorite.getCreatedAt()
        );
    }

    private void replaceTags(Long docId, List<String> tags) {
        tagMapper.delete(new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getDocId, docId));
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tagName : tags.stream().filter(tag -> tag != null && !tag.isBlank()).distinct().toList()) {
            createTag(docId, tagName.trim());
        }
    }

    private List<KnowledgeTagItem> listTags(Long docId) {
        return tagMapper.selectList(new LambdaQueryWrapper<KnowledgeTag>()
                        .eq(KnowledgeTag::getDocId, docId)
                        .orderByAsc(KnowledgeTag::getId))
                .stream()
                .map(this::toTagItem)
                .toList();
    }

    private KnowledgeTagItem toTagItem(KnowledgeTag tag) {
        return new KnowledgeTagItem(tag.getId(), tag.getDocId(), tag.getTagName(), tag.getCreatedAt());
    }

    private LambdaQueryWrapper<KnowledgeDoc> baseDocWrapper(Long spaceId, String keyword) {
        return new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0)
                .eq(spaceId != null, KnowledgeDoc::getSpaceId, spaceId)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(KnowledgeDoc::getTitle, keyword)
                        .or()
                        .like(KnowledgeDoc::getContentText, keyword));
    }

    private KnowledgeSpace getSpaceEntity(Long id) {
        KnowledgeSpace space = spaceMapper.selectById(id);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException("知识空间不存在");
        }
        return space;
    }

    private KnowledgeDoc getDocEntity(Long id) {
        KnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null || doc.getDeleted() == 1) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }

    private Long defaultTeamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().eq(Team::getDeleted, 0).last("LIMIT 1"));
        return team == null ? 0L : team.getId();
    }

    private Map<Long, KnowledgeSpace> loadSpaces(List<Long> spaceIds) {
        List<Long> ids = spaceIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return spaceMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(KnowledgeSpace::getId, Function.identity()));
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

    private String markdownToText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("[#>*_`~\\-+|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

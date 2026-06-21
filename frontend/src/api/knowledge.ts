import http from './request'
import type { PageResult } from './system'
import type { FileItem } from './file'

export interface KnowledgeTagItem {
  id: number
  docId: number
  tagName: string
  createdAt?: string
}

/** 知识空间列表项。visibility: PUBLIC(所有人可读) / TEAM(所属团队可读) / PRIVATE(本人)。 */
export interface KnowledgeSpaceItem {
  id: number
  teamId: number
  spaceName: string
  description?: string
  /** PUBLIC / TEAM / PRIVATE */
  visibility: string
  ownerId: number
  ownerName?: string
  /** 文档总数（后端聚合计算，不含软删除文档）。 */
  docCount: number
  createdAt?: string
}

/** 文档详情/列表项，包含完整 Markdown 内容。docStatus: DRAFT / PUBLISHED。 */
export interface KnowledgeDocItem {
  id: number
  spaceId: number
  spaceName?: string
  parentId: number
  title: string
  /** 正文 Markdown 原文，编辑器和预览均使用此字段。 */
  contentMd: string
  /** 纯文本内容（用于 RAG 切块），前端展示通常用 contentMd。 */
  contentText?: string
  authorId: number
  authorName?: string
  /** DRAFT（草稿）或 PUBLISHED（已发布，触发 RAG 索引）。 */
  docStatus: string
  sortNo: number
  /** 每次 publish 递增，恢复历史版本时也会生成新版本号。 */
  versionNo: number
  favorite: boolean
  favoriteId?: number
  tags: KnowledgeTagItem[]
  createdAt?: string
  updatedAt?: string
}

/** 文档树节点，不含 contentMd（减少首屏传输量，点击后再按需加载详情）。 */
export interface KnowledgeDocTreeNode {
  id: number
  parentId: number
  title: string
  docStatus: string
  versionNo: number
  tags: KnowledgeTagItem[]
  children: KnowledgeDocTreeNode[]
}

export interface KnowledgeVersionItem {
  id: number
  docId: number
  versionNo: number
  title: string
  contentMd: string
  editorId: number
  editorName?: string
  changeSummary?: string
  createdAt?: string
}

/** 文件导入结果：后端解析 docx/txt 后自动创建文档，并可选立即发布触发 RAG 索引。 */
export interface KnowledgeImportResult {
  doc: KnowledgeDocItem
  file: FileItem
  /** autoPublish=true 时为 true，文档已自动发布并触发 RAG 索引入队。 */
  published: boolean
  indexed: boolean
  /** 提取模式，例如 'docx' / 'txt' / 'tika'。 */
  extractMode: string
}

export interface KnowledgeSpaceForm {
  teamId?: number
  spaceName: string
  description?: string
  visibility: string
}

export interface KnowledgeDocForm {
  spaceId: number
  parentId?: number
  title: string
  contentMd?: string
  docStatus?: string
  sortNo?: number
  tags?: string[]
}

export function knowledgeSpacePageApi(params: { page?: number; size?: number; keyword?: string }) {
  return http.get<unknown, PageResult<KnowledgeSpaceItem>>('/knowledge-spaces/page', { params })
}

export function createKnowledgeSpaceApi(data: KnowledgeSpaceForm) {
  return http.post<unknown, KnowledgeSpaceItem>('/knowledge-spaces', data)
}

export function updateKnowledgeSpaceApi(id: number, data: KnowledgeSpaceForm) {
  return http.put<unknown, KnowledgeSpaceItem>(`/knowledge-spaces/${id}`, data)
}

export function deleteKnowledgeSpaceApi(id: number) {
  return http.delete<unknown, null>(`/knowledge-spaces/${id}`)
}

export function knowledgeDocPageApi(params: { page?: number; size?: number; spaceId?: number; keyword?: string }) {
  return http.get<unknown, PageResult<KnowledgeDocItem>>('/knowledge-docs/page', { params })
}

export function knowledgeDocTreeApi(params: { spaceId?: number; keyword?: string }) {
  return http.get<unknown, KnowledgeDocTreeNode[]>('/knowledge-docs/tree', { params })
}

export function knowledgeDocDetailApi(id: number) {
  return http.get<unknown, KnowledgeDocItem>(`/knowledge-docs/${id}`)
}

export function createKnowledgeDocApi(data: KnowledgeDocForm) {
  return http.post<unknown, KnowledgeDocItem>('/knowledge-docs', data)
}

export function importKnowledgeDocFileApi(file: File, data: { spaceId: number; parentId?: number; title?: string; tags?: string[]; autoPublish?: boolean }) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('spaceId', String(data.spaceId))
  if (data.parentId !== undefined && data.parentId > 0) formData.append('parentId', String(data.parentId))
  if (data.title) formData.append('title', data.title)
  if (data.tags?.length) formData.append('tags', data.tags.join(','))
  if (data.autoPublish !== undefined) formData.append('autoPublish', String(data.autoPublish))
  return http.post<unknown, KnowledgeImportResult>('/knowledge-docs/import-file', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function updateKnowledgeDocApi(id: number, data: KnowledgeDocForm) {
  return http.put<unknown, KnowledgeDocItem>(`/knowledge-docs/${id}`, data)
}

export function deleteKnowledgeDocApi(id: number) {
  return http.delete<unknown, null>(`/knowledge-docs/${id}`)
}

export function publishKnowledgeDocApi(id: number, data: { changeSummary?: string }) {
  return http.post<unknown, KnowledgeDocItem>(`/knowledge-docs/${id}/publish`, data)
}

export function restoreKnowledgeDocApi(id: number, versionId: number) {
  return http.post<unknown, KnowledgeDocItem>(`/knowledge-docs/${id}/restore/${versionId}`)
}

export function knowledgeVersionPageApi(params: { page?: number; size?: number; docId?: number; keyword?: string }) {
  return http.get<unknown, PageResult<KnowledgeVersionItem>>('/knowledge-versions/page', { params })
}

export function createKnowledgeFavoriteApi(data: { docId: number }) {
  return http.post<unknown, { id: number }>('/knowledge-favorites', data)
}

export function deleteKnowledgeFavoriteApi(id: number) {
  return http.delete<unknown, null>(`/knowledge-favorites/${id}`)
}

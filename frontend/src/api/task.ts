import http from './request'
import type { PageResult } from './system'

export interface TaskTagItem {
  id: number
  taskId: number
  tagName: string
  tagColor: string
  createdAt?: string
}

export interface TaskListItem {
  id: number
  projectId: number
  projectName?: string
  parentId: number
  taskNo: string
  title: string
  description?: string
  assigneeId?: number
  assigneeName?: string
  executorIds: number[]
  executorNames: string[]
  reporterId?: number
  reporterName?: string
  priority: string
  status: string
  startTime?: string
  dueTime?: string
  estimateHours: number
  actualHours: number
  sortNo: number
  tags: TaskTagItem[]
  createdAt?: string
  updatedAt?: string
}

export interface TaskCommentItem {
  id: number
  taskId: number
  userId: number
  username?: string
  nickname?: string
  content: string
  createdAt?: string
}

export interface TaskWorklogItem {
  id: number
  taskId: number
  userId: number
  username?: string
  nickname?: string
  workDate?: string
  hours: number
  description?: string
  createdAt?: string
}

export interface TaskAttachmentItem {
  id: number
  taskId: number
  fileId: number
  fileName?: string
  contentType?: string
  fileSize?: number
  fileExt?: string
  createdBy: number
  uploaderName?: string
  createdAt?: string
}

export interface TaskDetail {
  task: TaskListItem
  executors: TaskExecutorItem[]
  comments: TaskCommentItem[]
  worklogs: TaskWorklogItem[]
  attachments: TaskAttachmentItem[]
  tags: TaskTagItem[]
}

export interface TaskExecutorItem {
  id: number
  taskId: number
  userId: number
  username?: string
  nickname?: string
  displayName?: string
  createdAt?: string
}

export interface KanbanColumn {
  status: string
  title: string
  tasks: TaskListItem[]
}

export interface GanttTaskItem {
  id: number
  taskNo: string
  title: string
  status: string
  assigneeName?: string
  startTime?: string
  dueTime?: string
  progress: number
}

export interface TaskForm {
  projectId: number
  parentId?: number
  taskNo?: string
  title: string
  description?: string
  assigneeId?: number
  executorIds?: number[]
  priority: string
  status: string
  startTime?: string
  dueTime?: string
  estimateHours: number
  sortNo?: number
  tags: Array<{
    taskId?: number
    tagName: string
    tagColor: string
  }>
}

export function taskPageApi(params: { page?: number; size?: number; projectId?: number; status?: string; keyword?: string }) {
  return http.get<unknown, PageResult<TaskListItem>>('/tasks/page', { params })
}

export function taskKanbanApi(params: { projectId?: number; keyword?: string }) {
  return http.get<unknown, KanbanColumn[]>('/tasks/kanban', { params })
}

export function taskGanttApi(params: { projectId?: number; keyword?: string }) {
  return http.get<unknown, GanttTaskItem[]>('/tasks/gantt', { params })
}

export function taskDetailApi(id: number) {
  return http.get<unknown, TaskDetail>(`/tasks/${id}`)
}

export function createTaskApi(data: TaskForm) {
  return http.post<unknown, TaskDetail>('/tasks', data)
}

export function updateTaskApi(id: number, data: TaskForm) {
  return http.put<unknown, TaskDetail>(`/tasks/${id}`, data)
}

export function updateTaskStatusApi(id: number, data: { status: string; sortNo?: number }) {
  return http.put<unknown, TaskListItem>(`/tasks/${id}/status`, data)
}

export function deleteTaskApi(id: number) {
  return http.delete<unknown, null>(`/tasks/${id}`)
}

export function createTaskCommentApi(data: { taskId: number; content: string }) {
  return http.post<unknown, TaskCommentItem>('/task-comments', data)
}

export function createTaskWorklogApi(data: { taskId: number; workDate?: string; hours: number; description?: string }) {
  return http.post<unknown, TaskWorklogItem>('/task-worklogs', data)
}

export function createTaskAttachmentApi(data: { taskId: number; fileId: number }) {
  return http.post<unknown, TaskAttachmentItem>('/task-attachments', data)
}

export function deleteTaskAttachmentApi(id: number) {
  return http.delete<unknown, null>(`/task-attachments/${id}`)
}

export function createTaskTagApi(data: { taskId: number; tagName: string; tagColor: string }) {
  return http.post<unknown, TaskTagItem>('/task-tags', data)
}

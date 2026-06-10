import http from './request'
import type { PageResult } from './system'

export interface TeamItem {
  id: number
  teamName: string
  teamCode: string
  ownerId?: number
  ownerName?: string
  description?: string
  status: number
  createdAt?: string
}

export interface ProjectTagItem {
  id: number
  projectId: number
  tagName: string
  tagColor: string
  createdAt?: string
}

export interface ProjectMemberItem {
  id: number
  projectId: number
  userId: number
  username: string
  nickname?: string
  email?: string
  projectRole: string
  createdAt?: string
}

export interface ProjectListItem {
  id: number
  teamId: number
  teamName?: string
  projectCode: string
  projectName: string
  description?: string
  ownerId: number
  ownerName?: string
  startDate?: string
  endDate?: string
  status: string
  progress: number
  memberCount: number
  tags: ProjectTagItem[]
  createdAt?: string
  updatedAt?: string
}

export interface ProjectDetail {
  project: ProjectListItem
  members: ProjectMemberItem[]
  tags: ProjectTagItem[]
}

export interface ProjectStats {
  total: number
  active: number
  done: number
  averageProgress: number
}

export interface ProjectForm {
  teamId: number
  projectCode: string
  projectName: string
  description?: string
  ownerId?: number
  startDate?: string
  endDate?: string
  status: string
  progress: number
  memberUserIds: number[]
  tags: Array<{
    projectId: number
    tagName: string
    tagColor: string
  }>
}

export interface ProjectMemberForm {
  projectId: number
  userId: number
  projectRole: string
}

export interface ProjectTagForm {
  projectId: number
  tagName: string
  tagColor: string
}

export interface TeamMemberItem {
  id: number
  teamId: number
  userId: number
  username: string
  nickname?: string
  memberRole: string
  joinTime?: string
  status: number
}

export interface TeamMemberForm {
  userId: number
  memberRole?: string
}

export function teamPageApi(params: { page?: number; size?: number; keyword?: string; status?: number }) {
  return http.get<unknown, PageResult<TeamItem>>('/teams/page', { params })
}

export function createTeamApi(data: {
  teamName: string; teamCode: string; ownerId?: number; description?: string; status?: number
}) {
  return http.post<unknown, TeamItem>('/teams', data)
}

export function updateTeamApi(id: number, data: {
  teamName: string; teamCode: string; ownerId?: number; description?: string; status?: number
}) {
  return http.put<unknown, TeamItem>(`/teams/${id}`, data)
}

export function updateTeamStatusApi(id: number, status: number) {
  return http.patch<unknown, null>(`/teams/${id}/status`, { status })
}

export function deleteTeamApi(id: number) {
  return http.delete<unknown, null>(`/teams/${id}`)
}

export function teamMembersApi(teamId: number) {
  return http.get<unknown, TeamMemberItem[]>(`/teams/${teamId}/members`)
}

export function addTeamMemberApi(teamId: number, data: TeamMemberForm) {
  return http.post<unknown, TeamMemberItem>(`/teams/${teamId}/members`, data)
}

export function removeTeamMemberApi(teamId: number, memberId: number) {
  return http.delete<unknown, null>(`/teams/${teamId}/members/${memberId}`)
}

export function updateTeamMemberRoleApi(teamId: number, memberId: number, memberRole: string) {
  return http.patch<unknown, TeamMemberItem>(`/teams/${teamId}/members/${memberId}/role`, { memberRole })
}

export function projectStatsApi() {
  return http.get<unknown, ProjectStats>('/projects/stats')
}

export function projectPageApi(params: { page?: number; size?: number; keyword?: string; teamId?: number }) {
  return http.get<unknown, PageResult<ProjectListItem>>('/projects/page', { params })
}

export function projectDetailApi(id: number) {
  return http.get<unknown, ProjectDetail>(`/projects/${id}`)
}

export function createProjectApi(data: ProjectForm) {
  return http.post<unknown, ProjectDetail>('/projects', data)
}

export function updateProjectApi(id: number, data: ProjectForm) {
  return http.put<unknown, ProjectDetail>(`/projects/${id}`, data)
}

export function deleteProjectApi(id: number) {
  return http.delete<unknown, null>(`/projects/${id}`)
}

export function projectMembersPageApi(params: { page?: number; size?: number; projectId?: number; keyword?: string }) {
  return http.get<unknown, PageResult<ProjectMemberItem>>('/project-members/page', { params })
}

export function createProjectMemberApi(data: ProjectMemberForm) {
  return http.post<unknown, ProjectMemberItem>('/project-members', data)
}

export function deleteProjectMemberApi(id: number) {
  return http.delete<unknown, null>(`/project-members/${id}`)
}

export function projectTagsPageApi(params: { page?: number; size?: number; projectId?: number; keyword?: string }) {
  return http.get<unknown, PageResult<ProjectTagItem>>('/project-tags/page', { params })
}

export function createProjectTagApi(data: ProjectTagForm) {
  return http.post<unknown, ProjectTagItem>('/project-tags', data)
}

export function deleteProjectTagApi(id: number) {
  return http.delete<unknown, null>(`/project-tags/${id}`)
}

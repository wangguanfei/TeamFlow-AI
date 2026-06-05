import http from './request'

export interface ProfileInfo {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
  email?: string
  mobile?: string
  status: number
  lastLoginTime?: string
  lastLoginIp?: string
  createdAt?: string
  updatedAt?: string
  roles: string[]
  permissions: string[]
}

export interface ProfileOverview {
  responsibleTaskCount: number
  executingTaskCount: number
  ownedProjectCount: number
  knowledgeDocCount: number
  fileCount: number
  aiSessionCount: number
  roleCount: number
  permissionCount: number
}

export interface ProfileUpdatePayload {
  nickname?: string
  avatarUrl?: string
  email?: string
  mobile?: string
}

export interface ProfilePasswordPayload {
  oldPassword: string
  newPassword: string
}

export function profileApi() {
  return http.get<unknown, ProfileInfo>('/profile')
}

export function updateProfileApi(data: ProfileUpdatePayload) {
  return http.put<unknown, ProfileInfo>('/profile', data)
}

export function uploadProfileAvatarApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<unknown, ProfileInfo>('/profile/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function updateProfilePasswordApi(data: ProfilePasswordPayload) {
  return http.put<unknown, null>('/profile/password', data)
}

export function profileOverviewApi() {
  return http.get<unknown, ProfileOverview>('/profile/overview')
}

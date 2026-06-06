import http from './request'

export interface PageResult<T> {
  page: number
  size: number
  total: number
  records: T[]
}

export interface RoleItem {
  id: number
  roleCode: string
  roleName: string
  scopeType: string
  sortNo: number
  status: number
  remark?: string
}

export interface PermissionItem {
  id: number
  permissionCode: string
  permissionName: string
  resourceType: string
  resourcePath?: string
}

export interface MenuItem {
  id: number
  parentId: number
  menuName: string
  path: string
  component?: string
  icon?: string
  permissionCode?: string
  menuType: string
  sortNo: number
  visible: number
}

export interface UserItem {
  id: number
  username: string
  nickname: string
  email?: string
  mobile?: string
  status: number
  lastLoginTime?: string
  roles: string[]
}

export interface UserCreateForm {
  username: string
  password: string
  nickname?: string
  email?: string
  mobile?: string
  status: number
  roleIds: number[]
}

export interface UserUpdateForm {
  nickname?: string
  email?: string
  mobile?: string
  status: number
}

export interface PageParams {
  page?: number
  size?: number
  keyword?: string
}

export function rolePageApi(params: PageParams) {
  return http.get<unknown, PageResult<RoleItem>>('/roles/page', { params })
}

export function createRoleApi(data: Partial<RoleItem>) {
  return http.post<unknown, RoleItem>('/roles', data)
}

export function updateRoleApi(id: number, data: Partial<RoleItem>) {
  return http.put<unknown, RoleItem>(`/roles/${id}`, data)
}

export function deleteRoleApi(id: number) {
  return http.delete<unknown, null>(`/roles/${id}`)
}

export function rolePermissionIdsApi(id: number) {
  return http.get<unknown, number[]>(`/roles/${id}/permission-ids`)
}

export function assignRolePermissionsApi(id: number, ids: number[]) {
  return http.put<unknown, null>(`/roles/${id}/permissions`, { ids })
}

export function permissionPageApi(params: PageParams) {
  return http.get<unknown, PageResult<PermissionItem>>('/permissions/page', { params })
}

export function createPermissionApi(data: Partial<PermissionItem>) {
  return http.post<unknown, PermissionItem>('/permissions', data)
}

export function updatePermissionApi(id: number, data: Partial<PermissionItem>) {
  return http.put<unknown, PermissionItem>(`/permissions/${id}`, data)
}

export function deletePermissionApi(id: number) {
  return http.delete<unknown, null>(`/permissions/${id}`)
}

export function menuPageApi(params: PageParams) {
  return http.get<unknown, PageResult<MenuItem>>('/menus/page', { params })
}

export function createMenuApi(data: Partial<MenuItem>) {
  return http.post<unknown, MenuItem>('/menus', data)
}

export function updateMenuApi(id: number, data: Partial<MenuItem>) {
  return http.put<unknown, MenuItem>(`/menus/${id}`, data)
}

export function deleteMenuApi(id: number) {
  return http.delete<unknown, null>(`/menus/${id}`)
}

export function userPageApi(params: PageParams) {
  return http.get<unknown, PageResult<UserItem>>('/users/page', { params })
}

export function userOptionsApi() {
  return http.get<unknown, UserItem[]>('/users/options')
}

export function createUserApi(data: UserCreateForm) {
  return http.post<unknown, UserItem>('/users', data)
}

export function updateUserApi(id: number, data: UserUpdateForm) {
  return http.put<unknown, UserItem>(`/users/${id}`, data)
}

export function resetUserPasswordApi(id: number, password: string) {
  return http.put<unknown, null>(`/users/${id}/password`, { password })
}

export function userRoleIdsApi(id: number) {
  return http.get<unknown, number[]>(`/users/${id}/role-ids`)
}

export function assignUserRolesApi(id: number, roleIds: number[]) {
  return http.put<unknown, null>(`/users/${id}/roles`, { roleIds })
}

export interface LoginLogItem {
  id: number
  userId?: number
  username?: string
  loginIp?: string
  loginLocation?: string
  browser?: string
  os?: string
  userAgent?: string
  status: number
  message?: string
  createdAt: string
}

export interface LoginLogQueryParams {
  page?: number
  size?: number
  username?: string
  status?: number | ''
  startTime?: string
  endTime?: string
}

export function loginLogPageApi(params: LoginLogQueryParams) {
  return http.get<unknown, PageResult<LoginLogItem>>('/login-logs/page', { params })
}

export function batchDeleteLoginLogApi(ids: number[]) {
  return http.post<unknown, null>('/login-logs/batch-delete', { ids })
}

export function cleanLoginLogApi(beforeDays: number) {
  return http.post<unknown, number>('/login-logs/clean', { beforeDays })
}

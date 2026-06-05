export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface UserSummary {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
  email?: string
  mobile?: string
}

export interface MenuRoute {
  id: number
  parentId: number
  name: string
  path: string
  component: string
  icon: string
  permissionCode: string
  type: string
  sortNo: number
  children: MenuRoute[]
}

export interface AuthPayload {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
  roles: string[]
  permissions: string[]
  menus: MenuRoute[]
}

export interface CurrentUserPayload {
  user: UserSummary
  roles: string[]
  permissions: string[]
  menus: MenuRoute[]
}

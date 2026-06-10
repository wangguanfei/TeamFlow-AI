import http from './request'
import type { AuthPayload, CurrentUserPayload } from '@/types/api'

export interface LoginForm {
  username: string
  password: string
  rememberMe: boolean
}

export interface RegisterForm {
  username: string
  password: string
  nickname?: string
  email?: string
  mobile?: string
}

export function loginApi(data: LoginForm) {
  return http.post<unknown, AuthPayload>('/auth/login', data)
}

export function registerApi(data: RegisterForm) {
  return http.post<unknown, AuthPayload>('/auth/register', data)
}

export function meApi() {
  return http.get<unknown, CurrentUserPayload>('/auth/me')
}

export function logoutApi(refreshToken?: string) {
  return http.post<unknown, null>('/auth/logout', { refreshToken })
}

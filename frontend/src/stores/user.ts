import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loginApi, logoutApi, meApi, registerApi, type LoginForm, type RegisterForm } from '@/api/auth'
import type { AuthPayload, CurrentUserPayload, MenuRoute, UserSummary } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('teamflow_access_token') || '')
  const refreshToken = ref(localStorage.getItem('teamflow_refresh_token') || '')
  const user = ref<UserSummary | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const menus = ref<MenuRoute[]>([])
  const initialized = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(form: LoginForm) {
    const payload = await loginApi(form)
    setSession(payload)
  }

  async function register(form: RegisterForm) {
    const payload = await registerApi(form)
    setSession(payload)
  }

  async function fetchCurrentUser() {
    const payload = await meApi()
    setProfile(payload)
    initialized.value = true
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      clearSession()
    }
  }

  function hasPermission(permissionCode: string) {
    return permissions.value.includes(permissionCode)
  }

  function setSession(payload: AuthPayload) {
    token.value = payload.accessToken
    refreshToken.value = payload.refreshToken
    localStorage.setItem('teamflow_access_token', payload.accessToken)
    localStorage.setItem('teamflow_refresh_token', payload.refreshToken)
    setProfile(payload)
    initialized.value = true
  }

  function setProfile(payload: CurrentUserPayload) {
    user.value = payload.user
    roles.value = payload.roles
    permissions.value = payload.permissions
    menus.value = payload.menus
  }

  function clearSession() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    roles.value = []
    permissions.value = []
    menus.value = []
    initialized.value = false
    localStorage.removeItem('teamflow_access_token')
    localStorage.removeItem('teamflow_refresh_token')
  }

  return {
    token,
    refreshToken,
    user,
    roles,
    permissions,
    menus,
    initialized,
    isLoggedIn,
    login,
    register,
    fetchCurrentUser,
    logout,
    hasPermission,
    clearSession
  }
})

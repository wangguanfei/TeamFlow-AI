import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types/api'
import { doneProgress, startProgress } from '@/utils/progress'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  startProgress()
  const token = localStorage.getItem('teamflow_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, (error) => {
  doneProgress()
  return Promise.reject(error)
})

http.interceptors.response.use(
  ((response: AxiosResponse<ApiResult<unknown>>) => {
    doneProgress()
    const payload = response.data as ApiResult<unknown>
    if (payload.code !== 0) {
      ElMessage.error(payload.message || '请求失败')
      throw new Error(payload.message || '请求失败')
    }
    return payload.data
  }) as never,
  (error: AxiosError<ApiResult<unknown>>) => {
    doneProgress()
    const status = error.response?.status
    const silentError = !!(error.config as unknown as Record<string, unknown>)?.silentError
    const message = error.response?.data?.message || error.message || '网络异常'
    if (status === 401) {
      localStorage.removeItem('teamflow_access_token')
      localStorage.removeItem('teamflow_refresh_token')
      if (window.location.pathname !== '/login') {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
    } else if (!silentError) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default http

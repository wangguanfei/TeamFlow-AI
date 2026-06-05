import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types/api'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('teamflow_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  ((response: AxiosResponse<ApiResult<unknown>>) => {
    const payload = response.data as ApiResult<unknown>
    if (payload.code !== 0) {
      ElMessage.error(payload.message || '请求失败')
      throw new Error(payload.message || '请求失败')
    }
    return payload.data
  }) as never,
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    if (status === 401) {
      localStorage.removeItem('teamflow_access_token')
      localStorage.removeItem('teamflow_refresh_token')
      if (window.location.pathname !== '/login') {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default http

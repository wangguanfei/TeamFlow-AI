import http from './request'
import type { PageResult } from './system'

export interface NotificationItem {
  id: number
  title: string
  content?: string
  notifyType: string
  targetType: string
  targetId?: number
  senderId?: number
  senderName?: string
  read: boolean
  readTime?: string
  createdAt?: string
}

export interface NotificationForm {
  title: string
  content?: string
  notifyType?: string
  targetType?: string
  targetId?: number
}

export function notificationPageApi(params: { page?: number; size?: number; keyword?: string; unreadOnly?: boolean }) {
  return http.get<unknown, PageResult<NotificationItem>>('/notifications/page', { params })
}

export function createNotificationApi(data: NotificationForm) {
  return http.post<unknown, NotificationItem>('/notifications', data)
}

export function notificationUnreadCountApi() {
  return http.get<unknown, number>('/notifications/unread-count')
}

export function markNotificationReadApi(id: number) {
  return http.post<unknown, NotificationItem>(`/notifications/${id}/read`)
}

export function markAllNotificationsReadApi() {
  return http.post<unknown, null>('/notifications/read-all')
}

export function deleteNotificationApi(id: number) {
  return http.delete<unknown, null>(`/notifications/${id}`)
}

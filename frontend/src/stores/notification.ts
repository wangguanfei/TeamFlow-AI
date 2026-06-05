import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { notificationUnreadCountApi, type NotificationItem } from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const latest = ref<NotificationItem | null>(null)
  let socket: WebSocket | null = null

  async function refreshUnreadCount() {
    unreadCount.value = await notificationUnreadCountApi()
  }

  function connect() {
    const token = localStorage.getItem('teamflow_access_token')
    if (!token || socket) {
      return
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/ws/notifications`
    socket = new WebSocket(url)
    socket.onopen = () => {
      socket?.send(JSON.stringify({ type: 'AUTH', token }))
    }
    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as { type?: string; data?: NotificationItem }
        if (payload.type === 'NOTIFICATION_CREATED' && payload.data) {
          latest.value = payload.data
          unreadCount.value += 1
          ElMessage.info(payload.data.title)
        }
      } catch {
        // Ignore malformed realtime payloads; HTTP polling remains authoritative.
      }
    }
    socket.onclose = () => {
      socket = null
    }
    socket.onerror = () => {
      socket?.close()
    }
  }

  function disconnect() {
    socket?.close()
    socket = null
  }

  return {
    unreadCount,
    latest,
    refreshUnreadCount,
    connect,
    disconnect
  }
})

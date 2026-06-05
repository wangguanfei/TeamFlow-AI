<template>
  <PageContainer title="通知中心" description="查看系统、任务、项目和 AI 事件通知，支持实时推送和已读状态">
    <template #actions>
      <el-button :icon="Refresh" @click="loadNotifications">刷新</el-button>
      <PermissionButton permission="notification:read" :icon="Check" @click="markAllRead">全部已读</PermissionButton>
    </template>

    <section class="notification-page system-card">
      <div class="notification-toolbar">
        <el-input
          v-model="keyword"
          class="notification-search"
          placeholder="搜索通知标题、内容或类型"
          clearable
          :prefix-icon="Search"
          @keyup.enter="resetAndLoad"
          @clear="resetAndLoad"
        />
        <el-switch v-model="unreadOnly" active-text="仅看未读" @change="resetAndLoad" />
      </div>

      <div v-loading="loading" class="notification-list">
        <button
          v-for="item in notifications"
          :key="item.id"
          class="notification-item"
          :class="{ 'is-unread': !item.read }"
          @click="markRead(item)"
        >
          <span class="notification-item__icon" :class="`is-${item.notifyType.toLowerCase()}`">
            <el-icon><Bell /></el-icon>
          </span>
          <span class="notification-item__main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.content || '暂无内容' }}</small>
            <span class="notification-item__meta">
              <el-tag size="small" :type="tagType(item.notifyType)">{{ typeLabel(item.notifyType) }}</el-tag>
              <span>{{ item.senderName || '系统' }}</span>
              <span>{{ formatDate(item.createdAt) }}</span>
            </span>
          </span>
          <span class="notification-item__actions">
            <el-tag v-if="!item.read" size="small" type="danger">未读</el-tag>
            <el-tag v-else size="small" type="info">已读</el-tag>
            <PermissionButton permission="notification:delete" text type="danger" @click.stop="removeNotification(item)">
              删除
            </PermissionButton>
          </span>
        </button>
        <el-empty v-if="!loading && notifications.length === 0" description="暂无通知" />
      </div>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="table-pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50]"
        :total="total"
        @current-change="loadNotifications"
        @size-change="handleSizeChange"
      />
    </section>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, Refresh, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  deleteNotificationApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
  notificationPageApi,
  type NotificationItem
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { formatDateTime } from '@/utils/format'

const notificationStore = useNotificationStore()
const loading = ref(false)
const keyword = ref('')
const unreadOnly = ref(false)
const notifications = ref<NotificationItem[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

onMounted(() => {
  loadNotifications()
})

async function loadNotifications() {
  loading.value = true
  try {
    const result = await notificationPageApi({ page: page.value, size: size.value, keyword: keyword.value, unreadOnly: unreadOnly.value })
    notifications.value = result.records
    total.value = result.total
    await notificationStore.refreshUnreadCount()
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  loadNotifications()
}

function resetAndLoad() {
  page.value = 1
  loadNotifications()
}

async function markRead(item: NotificationItem) {
  if (item.read) {
    return
  }
  const updated = await markNotificationReadApi(item.id)
  const index = notifications.value.findIndex((notification) => notification.id === item.id)
  if (index >= 0) {
    notifications.value[index] = updated
  }
  await notificationStore.refreshUnreadCount()
}

async function markAllRead() {
  await markAllNotificationsReadApi()
  ElMessage.success('全部通知已标记为已读')
  await loadNotifications()
}

async function removeNotification(item: NotificationItem) {
  await ElMessageBox.confirm(`确定删除通知「${item.title}」吗？`, '删除通知', { type: 'warning' })
  await deleteNotificationApi(item.id)
  ElMessage.success('通知已删除')
  await loadNotifications()
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    SYSTEM: '系统',
    PROJECT: '项目',
    TASK: '任务',
    AI: 'AI'
  }
  return map[type] || type
}

function tagType(type: string) {
  if (type === 'TASK') return 'warning'
  if (type === 'AI') return 'success'
  if (type === 'PROJECT') return 'primary'
  return 'info'
}

function formatDate(value?: string) {
  return formatDateTime(value, '暂无时间')
}
</script>

<style scoped>
.notification-page {
  min-height: calc(100vh - 184px);
  padding: 20px;
}

.notification-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.notification-search {
  width: min(460px, 100%);
}

.notification-list {
  display: grid;
  gap: 10px;
}

.notification-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  background: #fff;
  color: var(--tf-text);
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.notification-item:hover {
  transform: translateY(-2px);
  border-color: rgba(37, 99, 235, 0.28);
  box-shadow: var(--tf-shadow-hover);
}

.notification-item.is-unread {
  border-color: rgba(37, 99, 235, 0.22);
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.06), rgba(124, 58, 237, 0.04)), #fff;
}

.notification-item__icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 12px;
  background: var(--tf-soft-blue);
  color: var(--tf-primary);
}

.notification-item__icon.is-task {
  background: #fffbeb;
  color: var(--tf-warning);
}

.notification-item__icon.is-ai {
  background: #ecfdf5;
  color: var(--tf-success);
}

.notification-item__main {
  min-width: 0;
  flex: 1;
}

.notification-item__main strong,
.notification-item__main small {
  display: block;
}

.notification-item__main strong {
  margin-bottom: 4px;
  font-size: 15px;
}

.notification-item__main small {
  color: var(--tf-muted);
  line-height: 1.6;
}

.notification-item__meta,
.notification-item__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notification-item__meta {
  margin-top: 10px;
  color: var(--tf-muted);
  font-size: 12px;
}

.notification-item__actions {
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .notification-toolbar,
  .notification-item {
    flex-direction: column;
  }

  .notification-item__actions {
    justify-content: flex-start;
  }
}
</style>

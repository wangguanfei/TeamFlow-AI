<template>
  <PageContainer title="通知中心" description="查看系统、任务、项目和 AI 事件通知，支持实时推送和已读状态">
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
        <div class="toolbar-actions">
          <el-switch v-model="unreadOnly" active-text="仅看未读" @change="resetAndLoad" />
          <el-button :icon="Refresh" @click="loadNotifications">刷新</el-button>
          <PermissionButton permission="notification:read" :icon="Check" @click="markAllRead">全部已读</PermissionButton>
        </div>
      </div>

      <div v-loading="loading" class="notification-list">
        <article
          v-for="item in notifications"
          :key="item.id"
          class="notification-item"
          :class="{ 'is-unread': !item.read }"
          role="button"
          tabindex="0"
          @click="openNotificationDetail(item)"
          @keydown.enter.prevent="openNotificationDetail(item)"
          @keydown.space.prevent="openNotificationDetail(item)"
        >
          <span class="notification-item__icon" :class="notificationTypeClass(item)">
            <el-icon><Bell /></el-icon>
          </span>
          <span class="notification-item__main">
            <span class="notification-item__heading">
              <strong>{{ item.title }}</strong>
              <span v-if="isTaskNotification(item)" class="notification-item__task-time" :class="{ 'is-empty': !item.bizTime }">
                <el-icon><Calendar /></el-icon>
                <span>任务时间</span>
                <b>{{ formatDate(item.bizTime) }}</b>
              </span>
            </span>
            <small>{{ item.content || '暂无内容' }}</small>
            <span class="notification-item__meta">
              <el-tag size="small" :type="tagType(item.notifyType)">{{ typeLabel(item.notifyType) }}</el-tag>
              <el-tag v-if="businessTypeLabel(item)" size="small" effect="plain" :type="businessTypeTag(item)">
                {{ businessTypeLabel(item) }}
              </el-tag>
              <span>{{ item.senderName || '系统' }}</span>
              <span v-if="taskReference(item)">{{ taskReference(item) }}</span>
            </span>
          </span>
          <span class="notification-item__side">
            <span class="notification-item__message-time">
              <el-icon><Clock /></el-icon>
              <span>消息时间</span>
              <b>{{ formatDate(item.createdAt) }}</b>
            </span>
            <span class="notification-item__actions">
              <el-tag v-if="!item.read" size="small" type="danger">未读</el-tag>
              <el-tag v-else size="small" type="info">已读</el-tag>
              <el-button text type="primary" :icon="View" @click.stop="openNotificationDetail(item)">
                详情
              </el-button>
              <el-button v-if="targetRoute(item)" text type="primary" :icon="ArrowRight" @click.stop="openTarget(item)">
                {{ targetLabel(item) }}
              </el-button>
              <PermissionButton permission="notification:delete" text type="danger" @click.stop="removeNotification(item)">
                删除
              </PermissionButton>
            </span>
          </span>
        </article>
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

    <el-dialog v-model="detailVisible" :title="activeNotification?.title || '通知详情'" width="640px">
      <div v-if="activeNotification" class="notification-detail">
        <div class="notification-detail__meta">
          <el-tag size="small" :type="tagType(activeNotification.notifyType)">{{ typeLabel(activeNotification.notifyType) }}</el-tag>
          <el-tag v-if="businessTypeLabel(activeNotification)" size="small" effect="plain" :type="businessTypeTag(activeNotification)">
            {{ businessTypeLabel(activeNotification) }}
          </el-tag>
          <span>{{ activeNotification.senderName || '系统' }}</span>
          <span>{{ formatDate(activeNotification.createdAt) }}</span>
        </div>
        <p class="notification-detail__content">{{ activeNotification.content || '暂无内容' }}</p>
        <div v-if="taskReference(activeNotification)" class="notification-detail__reference">
          {{ taskReference(activeNotification) }}
          <span v-if="activeNotification.bizTime"> · 任务时间 {{ formatDate(activeNotification.bizTime) }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="activeNotification && targetRoute(activeNotification)" type="primary" :icon="ArrowRight" @click="openTarget(activeNotification)">
          {{ targetLabel(activeNotification) }}
        </el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Bell, Calendar, Check, Clock, Refresh, Search, View } from '@element-plus/icons-vue'
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

const router = useRouter()
const notificationStore = useNotificationStore()
const loading = ref(false)
const keyword = ref('')
const unreadOnly = ref(false)
const notifications = ref<NotificationItem[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const activeNotification = ref<NotificationItem | null>(null)

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
    return item
  }
  const updated = await markNotificationReadApi(item.id)
  const index = notifications.value.findIndex((notification) => notification.id === item.id)
  if (index >= 0) {
    notifications.value[index] = updated
  }
  await notificationStore.refreshUnreadCount()
  return updated
}

async function openNotificationDetail(item: NotificationItem) {
  activeNotification.value = await markRead(item)
  detailVisible.value = true
}

async function openTarget(item: NotificationItem) {
  await markRead(item)
  const target = targetRoute(item)
  if (!target) {
    return
  }
  detailVisible.value = false
  await router.push(target)
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
    COMMENT: '评论',
    AI: 'AI',
    TASK_RISK: 'AI 任务预警',
    BUSINESS_BRIEF: 'AI 经营简报'
  }
  return map[type] || type
}

function tagType(type: string) {
  if (type === 'TASK_RISK') return 'danger'
  if (type === 'BUSINESS_BRIEF') return 'success'
  if (type === 'TASK' || type === 'COMMENT') return 'warning'
  if (type === 'AI') return 'success'
  if (type === 'PROJECT') return 'primary'
  return 'info'
}

function isTaskNotification(item: NotificationItem) {
  return isTaskRelated(item) || item.notifyType === 'TASK' || item.notifyType === 'COMMENT' || item.notifyType === 'TASK_RISK'
}

function taskReference(item: NotificationItem) {
  if (!isTaskRelated(item) || !item.bizId) {
    return ''
  }
  return `任务 #${item.bizId}`
}

function notificationTypeClass(item: NotificationItem) {
  return `is-${item.notifyType.toLowerCase().replace(/_/g, '-')}`
}

function isTaskRelated(item: NotificationItem) {
  return item.bizType === 'TASK' || Boolean(item.bizType?.startsWith('TASK_')) || item.notifyType === 'TASK_RISK'
}

function isBusinessBrief(item: NotificationItem) {
  return item.bizType === 'DAILY_BUSINESS_BRIEF' || item.notifyType === 'BUSINESS_BRIEF'
}

function businessTypeLabel(item: NotificationItem) {
  const map: Record<string, string> = {
    TASK_OVERDUE: '逾期',
    TASK_DUE_TODAY: '今日到期',
    TASK_HIGH_PRIORITY_STALE: '高优先级未更新',
    DAILY_BUSINESS_BRIEF: '每日简报'
  }
  return item.bizType ? map[item.bizType] || '' : ''
}

function businessTypeTag(item: NotificationItem) {
  if (item.bizType === 'TASK_OVERDUE') return 'danger'
  if (item.bizType === 'TASK_DUE_TODAY' || item.bizType === 'TASK_HIGH_PRIORITY_STALE') return 'warning'
  if (item.bizType === 'DAILY_BUSINESS_BRIEF') return 'success'
  return 'info'
}

function targetRoute(item: NotificationItem): RouteLocationRaw | null {
  if (isTaskRelated(item) && item.bizId) {
    return { path: '/task/list', query: { taskId: String(item.bizId) } }
  }
  if (isBusinessBrief(item)) {
    return { path: '/ai/chat' }
  }
  return null
}

function targetLabel(item: NotificationItem) {
  if (isTaskRelated(item)) {
    return '查看任务'
  }
  if (isBusinessBrief(item)) {
    return '打开AI助理'
  }
  return '查看'
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

.notification-item__icon.is-task-risk {
  background: #fef2f2;
  color: #dc2626;
}

.notification-item__icon.is-business-brief {
  background: #eef2ff;
  color: #4f46e5;
}

.notification-item__main {
  min-width: 0;
  flex: 1;
}

.notification-item__heading,
.notification-item__main strong,
.notification-item__main small {
  display: block;
}

.notification-item__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.notification-item__main strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item__main small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--tf-muted);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: break-word;
}

.notification-item__meta,
.notification-item__side,
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

.notification-item__side {
  width: 188px;
  flex: 0 0 188px;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.notification-item__task-time,
.notification-item__message-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  color: var(--tf-muted);
  font-size: 12px;
  white-space: nowrap;
}

.notification-item__task-time {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 8px;
  background: #fff7ed;
  color: #c2410c;
}

.notification-item__task-time.is-empty {
  background: #f8fafc;
  color: var(--tf-muted);
}

.notification-item__message-time {
  justify-content: flex-end;
  color: var(--tf-muted);
}

.notification-item__task-time b,
.notification-item__message-time b {
  font-weight: 600;
  color: var(--tf-text);
}

.notification-detail {
  display: grid;
  gap: 14px;
}

.notification-detail__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--tf-muted);
  font-size: 13px;
}

.notification-detail__content {
  margin: 0;
  padding: 14px;
  border: 1px solid var(--tf-border);
  border-radius: 10px;
  background: #f8fafc;
  color: var(--tf-text);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.notification-detail__reference {
  color: var(--tf-muted);
  font-size: 13px;
}

@media (max-width: 760px) {
  .notification-toolbar,
  .notification-item {
    flex-direction: column;
  }

  .notification-item__heading,
  .notification-item__side {
    width: 100%;
    align-items: flex-start;
  }

  .notification-item__heading {
    flex-direction: column;
  }

  .notification-item__side {
    flex-basis: auto;
  }

  .notification-item__message-time {
    justify-content: flex-start;
  }

  .notification-item__actions {
    justify-content: flex-start;
  }
}
</style>

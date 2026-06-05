<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="240px">
      <div class="brand">
        <div class="brand__mark">TF</div>
        <div>
          <strong>TeamFlow AI</strong>
          <span>企业协同平台</span>
        </div>
      </div>

      <el-menu :default-active="$route.path" router class="sidebar-menu">
        <template v-for="menu in userStore.menus" :key="menu.id">
          <el-menu-item v-if="menu.type === 'MENU'" :index="menu.path">
            <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
            <span>{{ menu.name }}</span>
          </el-menu-item>
          <el-sub-menu v-else :index="String(menu.id)">
            <template #title>
              <el-icon><component :is="menu.icon || 'Folder'" /></el-icon>
              <span>{{ menu.name }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.path">
              <span>{{ child.name }}</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header" height="72px">
        <div class="global-search-wrap">
          <el-input
            v-model="globalSearchKeyword"
            class="global-search"
            clearable
            placeholder="搜索项目、任务、知识文档"
            :prefix-icon="Search"
            @focus="handleGlobalSearchFocus"
            @blur="scheduleGlobalSearchClose"
            @clear="clearGlobalSearch"
            @keyup.enter="performGlobalSearch"
          />
          <div v-if="showGlobalSearchPanel" class="global-search-panel" @mousedown.prevent>
            <div class="global-search-panel__head">
              <strong>搜索结果</strong>
              <span>{{ globalSearchTotal }} 项</span>
            </div>
            <div v-if="globalSearchLoading" class="global-search-state">搜索中...</div>
            <template v-else-if="hasGlobalSearchResults">
              <section v-if="globalSearchProjects.length" class="global-search-section">
                <header>项目</header>
                <button v-for="project in globalSearchProjects" :key="`project-${project.id}`" class="global-search-result" @click="openGlobalSearchResult('project', project)">
                  <strong>{{ project.projectName }}</strong>
                  <span>{{ project.projectCode }} · {{ project.ownerName || '未设置负责人' }}</span>
                </button>
              </section>
              <section v-if="globalSearchTasks.length" class="global-search-section">
                <header>任务</header>
                <button v-for="task in globalSearchTasks" :key="`task-${task.id}`" class="global-search-result" @click="openGlobalSearchResult('task', task)">
                  <strong>{{ task.title }}</strong>
                  <span>{{ task.taskNo }} · {{ task.projectName || '未归属项目' }} · {{ statusLabel(task.status) }}</span>
                </button>
              </section>
              <section v-if="globalSearchDocs.length" class="global-search-section">
                <header>知识文档</header>
                <button v-for="doc in globalSearchDocs" :key="`doc-${doc.id}`" class="global-search-result" @click="openGlobalSearchResult('knowledge', doc)">
                  <strong>{{ doc.title }}</strong>
                  <span>{{ doc.spaceName || '知识空间' }} · v{{ doc.versionNo || 0 }}</span>
                </button>
              </section>
            </template>
            <div v-else class="global-search-state">未找到匹配内容</div>
          </div>
        </div>
        <div class="header-actions">
          <el-badge :value="notificationBadgeValue" class="notification-badge" :hidden="notificationStore.unreadCount === 0">
            <el-button :icon="Bell" circle title="通知" @click="router.push('/notification')" />
          </el-badge>
          <el-dropdown trigger="click">
            <button class="user-entry">
              <el-avatar :size="32" :src="userStore.user?.avatarUrl">{{ avatarText }}</el-avatar>
              <span>{{ userStore.user?.nickname || userStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item @click="router.push('/notification')">通知中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Bell, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { applyProfilePreferences, loadProfilePreferences } from '@/utils/profilePreferences'
import { projectPageApi, type ProjectListItem } from '@/api/project'
import { taskPageApi, type TaskListItem } from '@/api/task'
import { knowledgeDocPageApi, type KnowledgeDocItem } from '@/api/knowledge'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const globalSearchKeyword = ref('')
const globalSearchProjects = ref<ProjectListItem[]>([])
const globalSearchTasks = ref<TaskListItem[]>([])
const globalSearchDocs = ref<KnowledgeDocItem[]>([])
const globalSearchLoading = ref(false)
const globalSearchPanelVisible = ref(false)
let globalSearchTimer: number | undefined
let globalSearchCloseTimer: number | undefined
let globalSearchRequestId = 0

const avatarText = computed(() => {
  const name = userStore.user?.nickname || userStore.user?.username || 'U'
  return name.slice(0, 1).toUpperCase()
})

const notificationBadgeValue = computed(() => notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount)
const trimmedGlobalSearchKeyword = computed(() => globalSearchKeyword.value.trim())
const globalSearchTotal = computed(() => globalSearchProjects.value.length + globalSearchTasks.value.length + globalSearchDocs.value.length)
const hasGlobalSearchResults = computed(() => globalSearchTotal.value > 0)
const showGlobalSearchPanel = computed(() => globalSearchPanelVisible.value && !!trimmedGlobalSearchKeyword.value)

watch(globalSearchKeyword, (value) => {
  if (globalSearchTimer) {
    window.clearTimeout(globalSearchTimer)
  }
  if (!value.trim()) {
    resetGlobalSearchResults()
    return
  }
  globalSearchPanelVisible.value = true
  globalSearchTimer = window.setTimeout(() => performGlobalSearch(), 260)
})

onMounted(async () => {
  const preferences = loadProfilePreferences()
  applyProfilePreferences(preferences)
  await notificationStore.refreshUnreadCount()
  if (preferences.notificationRealtime) {
    notificationStore.connect()
  }
})

onBeforeUnmount(() => {
  if (globalSearchTimer) {
    window.clearTimeout(globalSearchTimer)
  }
  if (globalSearchCloseTimer) {
    window.clearTimeout(globalSearchCloseTimer)
  }
  notificationStore.disconnect()
})

function handleGlobalSearchFocus() {
  if (globalSearchCloseTimer) {
    window.clearTimeout(globalSearchCloseTimer)
  }
  if (trimmedGlobalSearchKeyword.value) {
    globalSearchPanelVisible.value = true
    if (!globalSearchTotal.value) {
      performGlobalSearch()
    }
  }
}

function scheduleGlobalSearchClose() {
  globalSearchCloseTimer = window.setTimeout(() => {
    globalSearchPanelVisible.value = false
  }, 160)
}

function clearGlobalSearch() {
  resetGlobalSearchResults()
  globalSearchPanelVisible.value = false
}

function resetGlobalSearchResults() {
  globalSearchRequestId += 1
  globalSearchProjects.value = []
  globalSearchTasks.value = []
  globalSearchDocs.value = []
  globalSearchLoading.value = false
}

async function performGlobalSearch() {
  const keyword = trimmedGlobalSearchKeyword.value
  if (!keyword) {
    resetGlobalSearchResults()
    return
  }
  const requestId = ++globalSearchRequestId
  globalSearchLoading.value = true
  globalSearchPanelVisible.value = true
  const [projects, tasks, docs] = await Promise.allSettled([
    projectPageApi({ page: 1, size: 5, keyword }),
    taskPageApi({ page: 1, size: 5, keyword }),
    knowledgeDocPageApi({ page: 1, size: 5, keyword })
  ])
  if (requestId !== globalSearchRequestId) {
    return
  }
  globalSearchProjects.value = projects.status === 'fulfilled' ? projects.value.records : []
  globalSearchTasks.value = tasks.status === 'fulfilled' ? tasks.value.records : []
  globalSearchDocs.value = docs.status === 'fulfilled' ? docs.value.records : []
  globalSearchLoading.value = false
}

function openGlobalSearchResult(type: 'project' | 'task' | 'knowledge', item: ProjectListItem | TaskListItem | KnowledgeDocItem) {
  const keyword = trimmedGlobalSearchKeyword.value
  globalSearchPanelVisible.value = false
  if (type === 'project') {
    const project = item as ProjectListItem
    router.push({ path: '/project/list', query: { keyword, projectId: String(project.id) } })
    return
  }
  if (type === 'task') {
    const task = item as TaskListItem
    router.push({ path: '/task/list', query: { keyword, taskId: String(task.id) } })
    return
  }
  const doc = item as KnowledgeDocItem
  router.push({ path: '/knowledge', query: { keyword, spaceId: String(doc.spaceId), docId: String(doc.id) } })
}

function statusLabel(status: string) {
  const map: Record<string, string> = { TODO: '待处理', DOING: '进行中', TESTING: '测试中', DONE: '已完成', CLOSED: '已关闭' }
  return map[status] || status
}

async function handleLogout() {
  await userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <PageContainer title="任务看板" description="按 TODO / DOING / TESTING / DONE 进行状态流转">
    <template #actions>
      <PermissionButton permission="task:create" type="primary" :icon="Plus" @click="openCreate">新建任务</PermissionButton>
    </template>

    <el-card shadow="never" class="system-card task-filter-card">
      <div class="table-toolbar">
        <div class="task-filter-group">
          <el-select v-model="projectId" class="task-project-select" clearable placeholder="全部项目" @change="loadBoard">
            <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
          <el-input v-model="keyword" class="table-search" placeholder="搜索任务标题、编号或描述" clearable :prefix-icon="Search" @keyup.enter="loadBoard" @clear="loadBoard" />
        </div>
        <el-button :icon="Search" @click="loadBoard">查询</el-button>
      </div>
    </el-card>

    <div class="kanban-board">
      <section v-for="column in columns" :key="column.status" class="kanban-column">
        <header>
          <div>
            <strong>{{ column.title }}</strong>
            <span>{{ column.status }}</span>
          </div>
          <el-tag>{{ column.total }}</el-tag>
        </header>
        <div
          :ref="(el) => setColumnRef(column.status, el)"
          class="kanban-task-list"
          :data-status="column.status"
        >
          <article v-for="task in column.tasks" :key="task.id" class="kanban-task-card" :data-task-id="task.id" @click="openDetail(task)">
            <div class="task-card-top">
              <span>{{ task.taskNo }}</span>
              <el-tag :type="priorityMeta(task.priority).type">{{ priorityMeta(task.priority).label }}</el-tag>
            </div>
            <h3>{{ task.title }}</h3>
            <p>{{ task.description || '暂无描述' }}</p>
            <div class="project-tags">
              <el-tag v-for="tag in task.tags" :key="tag.id" :style="tagStyle(tag.tagColor)">{{ tag.tagName }}</el-tag>
            </div>
            <footer>
              <span>负责人 {{ task.assigneeName || '未分配' }}</span>
              <span>{{ task.actualHours || 0 }}h / {{ task.estimateHours || 0 }}h</span>
            </footer>
            <div class="task-card-executors">
              <span>执行</span>
              <el-tag v-for="name in task.executorNames" :key="name" effect="plain">{{ name }}</el-tag>
              <em v-if="!task.executorNames?.length">未分配</em>
            </div>
          </article>
          <div v-if="column.loading && column.tasks.length === 0" class="kanban-column-state">加载中...</div>
          <div v-else-if="!column.loading && column.tasks.length === 0" class="kanban-column-state">暂无任务</div>
          <div v-if="column.tasks.length > 0 || column.total > 0" class="kanban-column-footer">
            <span>已加载 {{ column.tasks.length }} / {{ column.total }}</span>
            <el-button v-if="!column.finished" link type="primary" :loading="column.loading" @click.stop="loadMoreColumn(column)">
              加载更多
            </el-button>
          </div>
        </div>
      </section>
    </div>

    <el-dialog v-model="formVisible" title="新建任务" width="720px">
      <el-form :model="form" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目">
              <el-select v-model="form.projectId" filterable placeholder="选择项目">
                <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select v-model="form.assigneeId" filterable placeholder="选择负责人">
                <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="执行人">
          <el-select v-model="form.executorIds" filterable multiple collapse-tags collapse-tags-tooltip placeholder="选择一个或多个执行人">
            <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" placeholder="请输入任务标题" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="编号"><el-input v-model="form.taskNo" placeholder="留空自动生成" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级">
              <el-select v-model="form.priority">
                <el-option label="低" value="LOW" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="高" value="HIGH" />
                <el-option label="紧急" value="URGENT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option label="待处理" value="TODO" />
                <el-option label="进行中" value="DOING" />
                <el-option label="测试中" value="TESTING" />
                <el-option label="已完成" value="DONE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="预估工时"><el-input-number v-model="form.estimateHours" :min="0" :step="0.5" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="周期">
              <el-date-picker v-model="form.dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="截止时间" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签"><el-input v-model="form.tagsText" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存任务</el-button>
      </template>
    </el-dialog>

    <TaskDetailDrawer v-model="detailVisible" :task-id="detailTaskId" @changed="loadBoard" />
  </PageContainer>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import Sortable from 'sortablejs'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import TaskDetailDrawer from '@/views/task/TaskDetailDrawer.vue'
import { projectPageApi, type ProjectListItem } from '@/api/project'
import { userOptionsApi, type UserItem } from '@/api/system'
import {
  createTaskApi,
  taskPageApi,
  updateTaskStatusApi,
  type TaskListItem
} from '@/api/task'

const KANBAN_PAGE_SIZE = 10
const KANBAN_COLUMNS = [
  { status: 'TODO', title: '待处理' },
  { status: 'DOING', title: '进行中' },
  { status: 'TESTING', title: '测试中' },
  { status: 'DONE', title: '已完成' }
]

interface BoardColumn {
  status: string
  title: string
  tasks: TaskListItem[]
  page: number
  total: number
  loading: boolean
  finished: boolean
}

const keyword = ref('')
const projectId = ref<number | undefined>()
const projects = ref<ProjectListItem[]>([])
const users = ref<UserItem[]>([])
const columns = ref<BoardColumn[]>(createInitialColumns())
const formVisible = ref(false)
const detailVisible = ref(false)
const detailTaskId = ref<number | null>(null)
const columnRefs = new Map<string, HTMLElement>()
let sortableInstances: Sortable[] = []

const form = reactive({
  projectId: undefined as number | undefined,
  assigneeId: undefined as number | undefined,
  executorIds: [] as number[],
  taskNo: '',
  title: '',
  description: '',
  priority: 'MEDIUM',
  status: 'TODO',
  estimateHours: 4,
  dateRange: [] as string[],
  tagsText: ''
})

onMounted(async () => {
  await loadOptions()
  await loadBoard()
})

onBeforeUnmount(() => {
  destroySortables()
})

async function loadOptions() {
  const [projectResult, userResult] = await Promise.all([
    projectPageApi({ page: 1, size: 200 }),
    userOptionsApi()
  ])
  projects.value = projectResult.records
  users.value = userResult
}

async function loadBoard() {
  destroySortables()
  columns.value = createInitialColumns()
  await Promise.all(columns.value.map((column) => loadColumn(column.status, 1, true)))
  await nextTick()
  initSortables()
}

async function loadMoreColumn(column: BoardColumn) {
  await loadColumn(column.status, column.page + 1, false)
}

async function loadColumn(status: string, page: number, replace: boolean) {
  const column = findColumn(status)
  if (!column || column.loading) {
    return
  }
  column.loading = true
  try {
    const result = await taskPageApi({
      page,
      size: KANBAN_PAGE_SIZE,
      projectId: projectId.value,
      status,
      keyword: keyword.value
    })
    const existingIds = new Set(column.tasks.map((task) => task.id))
    const records = replace ? result.records : result.records.filter((task) => !existingIds.has(task.id))
    column.tasks = replace ? records : [...column.tasks, ...records]
    column.page = page
    column.total = result.total
    column.finished = column.tasks.length >= result.total || result.records.length < KANBAN_PAGE_SIZE
  } finally {
    column.loading = false
  }
}

function createInitialColumns(): BoardColumn[] {
  return KANBAN_COLUMNS.map((column) => ({
    ...column,
    tasks: [],
    page: 0,
    total: 0,
    loading: false,
    finished: false
  }))
}

function findColumn(status: string) {
  return columns.value.find((column) => column.status === status)
}

function setColumnRef(status: string, el: unknown) {
  if (el instanceof HTMLElement) {
    columnRefs.set(status, el)
  }
}

function initSortables() {
  destroySortables()
  for (const [status, element] of columnRefs.entries()) {
    const sortable = Sortable.create(element, {
      group: 'teamflow-kanban',
      animation: 160,
      draggable: '.kanban-task-card',
      ghostClass: 'kanban-task-card--ghost',
      dragClass: 'kanban-task-card--dragging',
      onEnd: async (event) => {
        const taskId = Number((event.item as HTMLElement).dataset.taskId)
        const sourceStatus = (event.from as HTMLElement).dataset.status || status
        const targetStatus = (event.to as HTMLElement).dataset.status || status
        if (!taskId || !targetStatus) return
        try {
          const sortNo = (event.newDraggableIndex ?? event.newIndex ?? 0) + 1
          await updateTaskStatusApi(taskId, { status: targetStatus, sortNo })
          ElMessage.success('任务状态已更新')
        } finally {
          await refreshMovedColumns(sourceStatus, targetStatus)
        }
      }
    })
    sortableInstances.push(sortable)
  }
}

function destroySortables() {
  sortableInstances.forEach((instance) => instance.destroy())
  sortableInstances = []
}

async function refreshMovedColumns(sourceStatus: string, targetStatus: string) {
  const statuses = Array.from(new Set([sourceStatus, targetStatus]))
  await Promise.all(statuses.map((status) => loadColumn(status, 1, true)))
  await nextTick()
  initSortables()
}

function openCreate() {
  Object.assign(form, {
    projectId: projectId.value || projects.value[0]?.id,
    assigneeId: users.value[0]?.id,
    executorIds: users.value[0]?.id ? [users.value[0].id] : [],
    taskNo: '',
    title: '',
    description: '',
    priority: 'MEDIUM',
    status: 'TODO',
    estimateHours: 4,
    dateRange: [],
    tagsText: ''
  })
  formVisible.value = true
}

async function saveTask() {
  if (!form.projectId || !form.title.trim()) {
    ElMessage.warning('请选择项目并填写标题')
    return
  }
  const [startTime, dueTime] = form.dateRange || []
  await createTaskApi({
    projectId: form.projectId,
    taskNo: form.taskNo,
    title: form.title,
    description: form.description,
    assigneeId: form.assigneeId,
    executorIds: form.executorIds,
    priority: form.priority,
    status: form.status,
    startTime,
    dueTime,
    estimateHours: form.estimateHours,
    tags: form.tagsText
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((tagName) => ({ tagName, tagColor: '#2563EB' }))
  })
  ElMessage.success('任务已创建')
  formVisible.value = false
  await loadBoard()
}

function openDetail(task: TaskListItem) {
  detailTaskId.value = task.id
  detailVisible.value = true
}

function priorityMeta(priority: string) {
  const map: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
    LOW: { label: '低', type: 'info' },
    MEDIUM: { label: '中', type: 'success' },
    HIGH: { label: '高', type: 'warning' },
    URGENT: { label: '紧急', type: 'danger' }
  }
  return map[priority] || { label: priority || '-', type: 'info' }
}

function tagStyle(color?: string) {
  const value = color || '#2563EB'
  return { color: value, borderColor: `${value}33`, background: `${value}12` }
}

function displayUser(user: UserItem) {
  return `${user.nickname || user.username} (${user.username})`
}
</script>

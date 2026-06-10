<template>
  <PageContainer title="任务列表" description="多条件查询、批量视图与任务详情协作">
    <template #actions>
      <PermissionButton permission="task:create" type="primary" :icon="Plus" @click="openCreate">新建任务</PermissionButton>
    </template>

    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <div class="task-filter-group">
          <el-select v-model="filters.teamId" clearable placeholder="全部团队" style="width:140px" @change="onTeamFilterChange">
            <el-option v-for="t in allTeams" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
          <el-select v-model="filters.projectId" class="task-project-select" clearable placeholder="全部项目" @change="loadData">
            <el-option v-for="project in filteredProjects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
          <el-select v-model="filters.status" class="task-status-select" clearable placeholder="全部状态" @change="loadData">
            <el-option label="待处理" value="TODO" />
            <el-option label="进行中" value="DOING" />
            <el-option label="测试中" value="TESTING" />
            <el-option label="已完成" value="DONE" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-input v-model="filters.keyword" class="table-search" placeholder="搜索任务编号、标题或描述" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        </div>
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" row-key="id">
        <el-table-column label="任务" min-width="260">
          <template #default="{ row }">
            <div class="project-name-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.taskNo }} · {{ row.projectName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }"><el-tag :type="priorityMeta(row.priority).type">{{ priorityMeta(row.priority).label }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="130" />
        <el-table-column label="执行人" min-width="180">
          <template #default="{ row }">
            <div class="task-user-tags">
              <el-tag v-for="name in row.executorNames" :key="name" effect="plain">{{ name }}</el-tag>
              <span v-if="!row.executorNames?.length" class="muted-text">未分配</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="工时" width="120">
          <template #default="{ row }">{{ row.actualHours || 0 }}h / {{ row.estimateHours || 0 }}h</template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <div class="project-tags">
              <el-tag v-for="tag in row.tags" :key="tag.id" :style="tagStyle(tag.tagColor)">{{ tag.tagName }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.dueTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <PermissionButton permission="task:update" link type="primary" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton permission="task:delete" link type="danger" @click="removeTask(row)">删除</PermissionButton>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :total="pageData.total"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑任务' : '新建任务'" width="720px">
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
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="编号"><el-input v-model="form.taskNo" /></el-form-item></el-col>
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
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="预估工时"><el-input-number v-model="form.estimateHours" :min="0" :step="0.5" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="1" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="周期">
          <el-date-picker v-model="form.dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="截止时间" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="标签"><el-input v-model="form.tagsText" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存任务</el-button>
      </template>
    </el-dialog>

    <TaskDetailDrawer v-model="detailVisible" :task-id="detailTaskId" @changed="loadData" />
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import TaskDetailDrawer from '@/views/task/TaskDetailDrawer.vue'
import { projectPageApi, teamPageApi, type ProjectListItem, type TeamItem } from '@/api/project'
import { userOptionsApi, type PageResult, type UserItem } from '@/api/system'
import {
  createTaskApi,
  deleteTaskApi,
  taskPageApi,
  updateTaskApi,
  type TaskForm,
  type TaskListItem
} from '@/api/task'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const pageData = ref<PageResult<TaskListItem>>({ page: 1, size: 10, total: 0, records: [] })
const projects = ref<ProjectListItem[]>([])
const allTeams = ref<TeamItem[]>([])
const filteredProjects = computed(() =>
  filters.teamId ? projects.value.filter(p => p.teamId === filters.teamId) : projects.value
)
const users = ref<UserItem[]>([])
const formVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailTaskId = ref<number | null>(null)

const filters = reactive({
  projectId: undefined as number | undefined,
  teamId: undefined as number | undefined,
  status: '',
  keyword: ''
})

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
  sortNo: 1,
  dateRange: [] as string[],
  tagsText: ''
})

onMounted(async () => {
  await loadOptions()
  applyRouteQueryFilters()
  await loadData()
  openDetailFromQuery()
  await openCreateFromQuery()
})

watch(
  () => route.fullPath,
  async () => {
    const changed = applyRouteQueryFilters()
    if (changed) {
      page.value = 1
      await loadData()
    }
    openDetailFromQuery()
    await openCreateFromQuery()
  }
)

async function loadOptions() {
  const [projectResult, userResult, teamResult] = await Promise.all([
    projectPageApi({ page: 1, size: 200 }),
    userOptionsApi(),
    teamPageApi({ page: 1, size: 200, status: 1 })
  ])
  projects.value = projectResult.records
  users.value = userResult
  allTeams.value = teamResult.records
}

function onTeamFilterChange() {
  filters.projectId = undefined
  page.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    pageData.value = await taskPageApi({
      page: page.value,
      size: size.value,
      projectId: filters.projectId,
      status: filters.status,
      keyword: filters.keyword,
      teamId: filters.teamId
    })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    projectId: filters.projectId || projects.value[0]?.id,
    assigneeId: users.value[0]?.id,
    executorIds: users.value[0]?.id ? [users.value[0].id] : [],
    taskNo: '',
    title: '',
    description: '',
    priority: 'MEDIUM',
    status: 'TODO',
    estimateHours: 4,
    sortNo: 1,
    dateRange: [],
    tagsText: ''
  })
  formVisible.value = true
}

async function openCreateFromQuery() {
  if (route.query.create !== '1') {
    return
  }
  if (!userStore.hasPermission('task:create')) {
    return
  }
  openCreate()
  const projectId = parseProjectQuery()
  if (projectId) {
    form.projectId = projectId
  }
  const { create, ...query } = route.query
  await router.replace({ path: route.path, query })
}

function openEdit(row: TaskListItem) {
  editingId.value = row.id
  Object.assign(form, {
    projectId: row.projectId,
    assigneeId: row.assigneeId,
    executorIds: [...(row.executorIds || [])],
    taskNo: row.taskNo,
    title: row.title,
    description: row.description || '',
    priority: row.priority,
    status: row.status,
    estimateHours: Number(row.estimateHours || 0),
    sortNo: row.sortNo || 100,
    dateRange: row.startTime && row.dueTime ? [row.startTime, row.dueTime] : [],
    tagsText: ''
  })
  formVisible.value = true
}

async function saveTask() {
  if (!form.projectId || !form.title.trim()) {
    ElMessage.warning('请选择项目并填写标题')
    return
  }
  const payload = buildPayload()
  if (editingId.value) {
    await updateTaskApi(editingId.value, payload)
  } else {
    await createTaskApi(payload)
  }
  ElMessage.success('任务已保存')
  formVisible.value = false
  await loadData()
}

async function removeTask(row: TaskListItem) {
  await ElMessageBox.confirm(`确认删除任务 ${row.title}？`, '删除确认', { type: 'warning' })
  await deleteTaskApi(row.id)
  ElMessage.success('任务已删除')
  await loadData()
}

function openDetail(row: TaskListItem) {
  detailTaskId.value = row.id
  detailVisible.value = true
}

function buildPayload(): TaskForm {
  const [startTime, dueTime] = form.dateRange || []
  return {
    projectId: form.projectId as number,
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
    sortNo: form.sortNo,
    tags: form.tagsText
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((tagName) => ({ tagName, tagColor: '#2563EB' }))
  }
}

function statusLabel(status: string) {
  const map: Record<string, string> = { TODO: '待处理', DOING: '进行中', TESTING: '测试中', DONE: '已完成', CLOSED: '已关闭' }
  return map[status] || status
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

function formatDate(value?: string) {
  return formatDateTime(value)
}

function applyRouteQueryFilters() {
  let changed = false
  const projectId = parseProjectQuery()
  if (filters.projectId !== projectId) {
    filters.projectId = projectId
    changed = true
  }
  const nextKeyword = parseStringQuery(route.query.keyword)
  if (filters.keyword !== nextKeyword) {
    filters.keyword = nextKeyword
    changed = true
  }
  return changed
}

function openDetailFromQuery() {
  const taskId = parseNumberQuery(route.query.taskId)
  if (!taskId) {
    return
  }
  detailTaskId.value = taskId
  detailVisible.value = true
}

function parseProjectQuery() {
  const raw = Array.isArray(route.query.projectId) ? route.query.projectId[0] : route.query.projectId
  const value = Number(raw)
  return Number.isFinite(value) && value > 0 ? value : undefined
}

function parseStringQuery(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw : ''
}

function parseNumberQuery(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const number = Number(raw)
  return Number.isFinite(number) && number > 0 ? number : undefined
}
</script>

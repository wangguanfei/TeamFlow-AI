<template>
  <PageContainer title="项目" description="项目进度、成员协作与标签沉淀">
    <template #actions>
      <PermissionButton permission="project:create" type="primary" :icon="Plus" @click="openCreate">新建项目</PermissionButton>
    </template>

    <div class="project-stats">
      <div class="project-stat-card">
        <span>项目总数</span>
        <strong>{{ stats.total }}</strong>
        <small>团队项目资产</small>
      </div>
      <div class="project-stat-card">
        <span>进行中</span>
        <strong>{{ stats.active }}</strong>
        <small>ACTIVE 状态</small>
      </div>
      <div class="project-stat-card">
        <span>已完成</span>
        <strong>{{ stats.done }}</strong>
        <small>DONE 状态</small>
      </div>
      <div class="project-stat-card">
        <span>平均进度</span>
        <strong>{{ Number(stats.averageProgress || 0).toFixed(0) }}%</strong>
        <small>实时统计</small>
      </div>
    </div>

    <div v-if="recentProject" class="project-success-panel">
      <div class="project-success-icon">
        <el-icon><CircleCheck /></el-icon>
      </div>
      <div class="project-success-copy">
        <strong>项目已创建</strong>
        <span>{{ recentProject.projectName }} 已加入项目列表，可继续补充成员与任务计划。</span>
      </div>
      <div class="project-success-actions">
        <PermissionButton permission="project:member" :icon="UserFilled" @click="openMembers(recentProject)">添加成员</PermissionButton>
        <PermissionButton permission="task:create" type="primary" :icon="Tickets" @click="createTaskForProject(recentProject)">创建任务</PermissionButton>
        <el-button :icon="View" @click="openDetail(recentProject)">查看详情</el-button>
        <el-button class="project-success-close" :icon="Close" circle aria-label="关闭提示" @click="recentProject = null" />
      </div>
    </div>

    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input v-model="keyword" class="table-search" placeholder="搜索项目名称、编码或描述" clearable :prefix-icon="Search" @keyup.enter="() => { page = 1; loadData() }" />
        <el-select v-model="teamIdFilter" placeholder="全部团队" clearable style="width:160px" @change="() => { page = 1; loadData() }">
          <el-option v-for="t in activeTeams" :key="t.id" :label="t.teamName" :value="t.id" />
        </el-select>
        <el-button :icon="Search" @click="() => { page = 1; loadData() }">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" row-key="id" :row-class-name="tableRowClassName">
        <el-table-column label="项目" min-width="260">
          <template #default="{ row }">
            <div class="project-name-cell">
              <strong>{{ row.projectName }}</strong>
              <span>{{ row.projectCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="teamName" label="团队" min-width="150" />
        <el-table-column prop="ownerName" label="负责人" min-width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="190">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progress || 0)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <div class="project-tags">
              <el-tag v-for="tag in row.tags" :key="tag.id" :style="tagStyle(tag.tagColor)">{{ tag.tagName }}</el-tag>
              <span v-if="!row.tags?.length" class="muted-text">未设置</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="memberCount" label="成员" width="90" />
        <el-table-column label="周期" min-width="180">
          <template #default="{ row }">{{ formatRange(row.startDate, row.endDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <PermissionButton permission="project:update" link type="primary" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton permission="project:member" link type="primary" @click="openMembers(row)">成员</PermissionButton>
            <PermissionButton permission="project:tag" link type="primary" @click="openTags(row)">标签</PermissionButton>
            <PermissionButton permission="project:delete" link type="danger" @click="removeProject(row)">删除</PermissionButton>
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

    <el-dialog v-model="formVisible" :title="editingId ? '编辑项目' : '新建项目'" width="760px">
      <el-form :model="form" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目编码"><el-input v-model="form.projectCode" placeholder="如 TF-CRM" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称"><el-input v-model="form.projectName" placeholder="请输入项目名称" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="团队">
              <el-select v-model="form.teamId" placeholder="选择团队" filterable style="width:100%">
                <el-option v-for="team in teams" :key="team.id" :label="team.teamName" :value="team.id" />
                <template v-if="teams.length === 0" #empty>
                  <div style="padding:12px;text-align:center;color:#909399;font-size:13px">
                    暂无可用团队，
                    <el-link type="primary" @click="goToTeamManage">前往创建团队</el-link>
                  </div>
                </template>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select v-model="form.ownerId" placeholder="选择负责人" filterable>
                <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option label="规划中" value="PLANNING" />
                <el-option label="进行中" value="ACTIVE" />
                <el-option label="已完成" value="DONE" />
                <el-option label="已归档" value="ARCHIVED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进度">
              <el-input-number v-model="form.progress" :min="0" :max="100" :step="5" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="计划周期">
          <el-date-picker
            v-model="form.dateRange"
            type="daterange"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始成员">
          <el-select v-model="form.memberUserIds" multiple filterable placeholder="可选择多个成员">
            <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingId" label="标签">
          <el-input v-model="form.tagsText" placeholder="多个标签用逗号分隔，如 AI协同, 面试演示" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="项目目标、范围和关键交付物" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProject">保存项目</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="项目详情" size="520px">
      <div v-if="detail" class="project-detail">
        <h3>{{ detail.project.projectName }}</h3>
        <p>{{ detail.project.description || '暂无描述' }}</p>
        <div class="detail-grid">
          <span>项目编码</span><strong>{{ detail.project.projectCode }}</strong>
          <span>团队</span><strong>{{ detail.project.teamName || '-' }}</strong>
          <span>负责人</span><strong>{{ detail.project.ownerName || '-' }}</strong>
          <span>状态</span><strong>{{ statusMeta(detail.project.status).label }}</strong>
          <span>计划周期</span><strong>{{ formatRange(detail.project.startDate, detail.project.endDate) }}</strong>
          <span>成员数</span><strong>{{ detail.members.length }}</strong>
        </div>
        <el-progress class="detail-progress" :percentage="Number(detail.project.progress || 0)" />
        <h4>成员</h4>
        <div class="member-list">
          <div v-for="member in detail.members" :key="member.id" class="member-row">
            <el-avatar :size="30">{{ (member.nickname || member.username || 'U').slice(0, 1) }}</el-avatar>
            <div>
              <strong>{{ member.nickname || member.username }}</strong>
              <span>{{ member.email || member.username }}</span>
            </div>
            <el-tag>{{ member.projectRole }}</el-tag>
          </div>
        </div>
        <h4>标签</h4>
        <div class="project-tags project-tags--large">
          <el-tag v-for="tag in detail.tags" :key="tag.id" :style="tagStyle(tag.tagColor)">{{ tag.tagName }}</el-tag>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="memberVisible" title="项目成员" size="560px">
      <div v-if="currentProject" class="drawer-section">
        <h3>{{ currentProject.projectName }}</h3>
        <div class="inline-form">
          <el-select v-model="memberForm.userId" filterable placeholder="选择用户">
            <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
          </el-select>
          <el-select v-model="memberForm.projectRole" class="role-select">
            <el-option label="项目经理" value="PM" />
            <el-option label="开发" value="DEV" />
            <el-option label="测试" value="TEST" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
          <el-button type="primary" @click="inviteMember">邀请</el-button>
        </div>
        <div class="member-list">
          <div v-for="member in members" :key="member.id" class="member-row">
            <el-avatar :size="30">{{ (member.nickname || member.username || 'U').slice(0, 1) }}</el-avatar>
            <div>
              <strong>{{ member.nickname || member.username }}</strong>
              <span>{{ member.email || member.username }}</span>
            </div>
            <el-tag>{{ member.projectRole }}</el-tag>
            <el-button link type="danger" @click="removeMember(member)">移除</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="tagVisible" title="项目标签" size="440px">
      <div v-if="currentProject" class="drawer-section">
        <h3>{{ currentProject.projectName }}</h3>
        <div class="inline-form">
          <el-input v-model="tagForm.tagName" placeholder="标签名称" />
          <el-color-picker v-model="tagForm.tagColor" />
          <el-button type="primary" @click="addTag">添加</el-button>
        </div>
        <div class="tag-editor-list">
          <div v-for="tag in tags" :key="tag.id" class="tag-editor-row">
            <el-tag :style="tagStyle(tag.tagColor)">{{ tag.tagName }}</el-tag>
            <span>{{ tag.tagColor }}</span>
            <el-button link type="danger" @click="removeTag(tag)">删除</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Close, Plus, Search, Tickets, UserFilled, View } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { userPageApi, type PageResult, type UserItem } from '@/api/system'
import {
  createProjectApi,
  createProjectMemberApi,
  createProjectTagApi,
  deleteProjectApi,
  deleteProjectMemberApi,
  deleteProjectTagApi,
  projectDetailApi,
  projectMembersPageApi,
  projectPageApi,
  projectStatsApi,
  projectTagsPageApi,
  teamPageApi,
  updateProjectApi,
  type ProjectDetail,
  type ProjectForm,
  type ProjectListItem,
  type ProjectMemberItem,
  type ProjectStats,
  type ProjectTagItem,
  type TeamItem
} from '@/api/project'
import { useUserStore } from '@/stores/user'
import { formatDateRange } from '@/utils/format'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const pageData = ref<PageResult<ProjectListItem>>({ page: 1, size: 10, total: 0, records: [] })
const stats = ref<ProjectStats>({ total: 0, active: 0, done: 0, averageProgress: 0 })

const teams = ref<TeamItem[]>([])
const activeTeams = ref<TeamItem[]>([])
const teamIdFilter = ref<number | undefined>(undefined)
const users = ref<UserItem[]>([])
const formVisible = ref(false)
const detailVisible = ref(false)
const memberVisible = ref(false)
const tagVisible = ref(false)
const editingId = ref<number | null>(null)
const detail = ref<ProjectDetail | null>(null)
const currentProject = ref<ProjectListItem | null>(null)
const members = ref<ProjectMemberItem[]>([])
const tags = ref<ProjectTagItem[]>([])
const recentProject = ref<ProjectListItem | null>(null)
const highlightedProjectId = ref<number | null>(null)
let highlightTimer: number | undefined

const form = reactive({
  teamId: undefined as number | undefined,
  projectCode: '',
  projectName: '',
  description: '',
  ownerId: undefined as number | undefined,
  dateRange: [] as string[],
  status: 'PLANNING',
  progress: 0,
  memberUserIds: [] as number[],
  tagsText: ''
})

const memberForm = reactive({
  userId: undefined as number | undefined,
  projectRole: 'DEV'
})

const tagForm = reactive({
  tagName: '',
  tagColor: '#2563EB'
})

onMounted(async () => {
  applySearchQuery()
  teamPageApi({ page: 1, size: 200, status: 1 }).then(r => { activeTeams.value = r.records })
  await loadData()
  await openCreateFromQuery()
  await openProjectFromQuery()
})

watch(
  () => route.fullPath,
  async () => {
    const searchChanged = applySearchQuery()
    if (searchChanged) {
      page.value = 1
      await loadData()
    }
    await openCreateFromQuery()
    await openProjectFromQuery()
  }
)

onBeforeUnmount(() => {
  if (highlightTimer) {
    window.clearTimeout(highlightTimer)
  }
})

async function loadData() {
  loading.value = true
  try {
    const [projects, projectStats] = await Promise.all([
      projectPageApi({ page: page.value, size: size.value, keyword: keyword.value, teamId: teamIdFilter.value }),
      projectStatsApi()
    ])
    pageData.value = projects
    stats.value = projectStats
  } finally {
    loading.value = false
  }
}

async function loadSelectOptions() {
  const [teamResult, userResult] = await Promise.all([
    teamPageApi({ page: 1, size: 200, status: 1 }),
    userPageApi({ page: 1, size: 200 })
  ])
  teams.value = teamResult.records
  activeTeams.value = teamResult.records
  users.value = userResult.records
}

function goToTeamManage() {
  router.push('/system/team')
}

async function openCreate() {
  await loadSelectOptions()
  editingId.value = null
  Object.assign(form, {
    teamId: teams.value[0]?.id,
    projectCode: '',
    projectName: '',
    description: '',
    ownerId: userStore.user?.id || users.value[0]?.id,
    dateRange: [],
    status: 'PLANNING',
    progress: 0,
    memberUserIds: [],
    tagsText: ''
  })
  formVisible.value = true
}

async function openCreateFromQuery() {
  if (route.query.create !== '1') {
    return
  }
  if (!userStore.hasPermission('project:create')) {
    return
  }
  await openCreate()
  const { create, ...query } = route.query
  await router.replace({ path: route.path, query })
}

async function openProjectFromQuery() {
  const projectId = parseNumberQuery(route.query.projectId)
  if (!projectId) {
    return
  }
  const project = pageData.value.records.find((item) => item.id === projectId)
  if (project) {
    await openDetail(project)
    return
  }
  detail.value = await projectDetailApi(projectId)
  detailVisible.value = true
}

async function openEdit(row: ProjectListItem) {
  await loadSelectOptions()
  editingId.value = row.id
  Object.assign(form, {
    teamId: row.teamId,
    projectCode: row.projectCode,
    projectName: row.projectName,
    description: row.description || '',
    ownerId: row.ownerId,
    dateRange: row.startDate && row.endDate ? [row.startDate, row.endDate] : [],
    status: row.status,
    progress: Number(row.progress || 0),
    memberUserIds: [],
    tagsText: ''
  })
  formVisible.value = true
}

async function saveProject() {
  if (!form.teamId) {
    ElMessage.warning('请选择团队')
    return
  }
  const payload = buildProjectPayload()
  let saved: ProjectDetail
  if (editingId.value) {
    saved = await updateProjectApi(editingId.value, payload)
  } else {
    saved = await createProjectApi(payload)
  }
  const isCreated = !editingId.value
  ElMessage.success(isCreated ? '项目已创建' : '项目已更新')
  formVisible.value = false
  if (isCreated) {
    page.value = 1
  }
  await loadData()
  if (isCreated) {
    showCreatedProject(saved.project)
  }
}

async function openDetail(row: ProjectListItem) {
  detail.value = await projectDetailApi(row.id)
  detailVisible.value = true
}

function createTaskForProject(row: ProjectListItem) {
  router.push({ path: '/task/list', query: { projectId: String(row.id), create: '1' } })
}

async function openMembers(row: ProjectListItem) {
  currentProject.value = row
  memberForm.userId = undefined
  memberForm.projectRole = 'DEV'
  await loadSelectOptions()
  await loadMembers(row.id)
  memberVisible.value = true
}

async function inviteMember() {
  if (!currentProject.value || !memberForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  await createProjectMemberApi({
    projectId: currentProject.value.id,
    userId: memberForm.userId,
    projectRole: memberForm.projectRole
  })
  ElMessage.success('成员已邀请')
  memberForm.userId = undefined
  await loadMembers(currentProject.value.id)
  await loadData()
}

async function removeMember(member: ProjectMemberItem) {
  await ElMessageBox.confirm(`确认移除 ${member.nickname || member.username}？`, '移除成员', { type: 'warning' })
  await deleteProjectMemberApi(member.id)
  ElMessage.success('成员已移除')
  if (currentProject.value) {
    await loadMembers(currentProject.value.id)
    await loadData()
  }
}

async function openTags(row: ProjectListItem) {
  currentProject.value = row
  tagForm.tagName = ''
  tagForm.tagColor = '#2563EB'
  await loadTags(row.id)
  tagVisible.value = true
}

async function addTag() {
  if (!currentProject.value || !tagForm.tagName.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  await createProjectTagApi({
    projectId: currentProject.value.id,
    tagName: tagForm.tagName.trim(),
    tagColor: tagForm.tagColor
  })
  ElMessage.success('标签已添加')
  tagForm.tagName = ''
  await loadTags(currentProject.value.id)
  await loadData()
}

async function removeTag(tag: ProjectTagItem) {
  await deleteProjectTagApi(tag.id)
  ElMessage.success('标签已删除')
  if (currentProject.value) {
    await loadTags(currentProject.value.id)
    await loadData()
  }
}

async function removeProject(row: ProjectListItem) {
  await ElMessageBox.confirm(`确认删除项目 ${row.projectName}？`, '删除确认', { type: 'warning' })
  await deleteProjectApi(row.id)
  ElMessage.success('项目已删除')
  await loadData()
}

async function loadMembers(projectId: number) {
  members.value = (await projectMembersPageApi({ page: 1, size: 200, projectId })).records
}

async function loadTags(projectId: number) {
  tags.value = (await projectTagsPageApi({ page: 1, size: 200, projectId })).records
}

function buildProjectPayload(): ProjectForm {
  const [startDate, endDate] = form.dateRange || []
  return {
    teamId: form.teamId as number,
    projectCode: form.projectCode,
    projectName: form.projectName,
    description: form.description,
    ownerId: form.ownerId,
    startDate,
    endDate,
    status: form.status,
    progress: form.progress,
    memberUserIds: form.memberUserIds,
    tags: form.tagsText
      .split(',')
      .map((name) => name.trim())
      .filter(Boolean)
      .map((tagName) => ({ projectId: 0, tagName, tagColor: '#2563EB' }))
  }
}

function statusMeta(status: string) {
  const map: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' }> = {
    PLANNING: { label: '规划中', type: 'info' },
    ACTIVE: { label: '进行中', type: 'primary' },
    DONE: { label: '已完成', type: 'success' },
    ARCHIVED: { label: '已归档', type: 'warning' }
  }
  return map[status] || { label: status || '-', type: 'info' }
}

function tagStyle(color?: string) {
  const value = color || '#2563EB'
  return {
    color: value,
    borderColor: `${value}33`,
    background: `${value}12`
  }
}

function displayUser(user: UserItem) {
  return `${user.nickname || user.username} (${user.username})`
}

function formatRange(start?: string, end?: string) {
  return formatDateRange(start, end, '-')
}

function showCreatedProject(project: ProjectListItem) {
  recentProject.value = pageData.value.records.find((item) => item.id === project.id) || project
  highlightedProjectId.value = project.id
  if (highlightTimer) {
    window.clearTimeout(highlightTimer)
  }
  highlightTimer = window.setTimeout(() => {
    highlightedProjectId.value = null
  }, 5000)
}

function tableRowClassName({ row }: { row: ProjectListItem }) {
  return row.id === highlightedProjectId.value ? 'project-row--created' : ''
}

function applySearchQuery() {
  const nextKeyword = parseStringQuery(route.query.keyword)
  if (nextKeyword === keyword.value) {
    return false
  }
  keyword.value = nextKeyword
  return true
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

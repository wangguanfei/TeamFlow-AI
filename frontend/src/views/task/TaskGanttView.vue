<template>
  <PageContainer title="甘特图" description="按任务计划周期展示进度和负责人">
    <el-card shadow="never" class="system-card task-filter-card">
      <div class="table-toolbar">
        <div class="task-filter-group">
          <el-select v-model="teamId" clearable placeholder="全部团队" style="width:140px" @change="onTeamChange">
            <el-option v-for="t in allTeams" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
          <el-select v-model="projectId" class="task-project-select" clearable placeholder="全部项目" @change="loadData">
            <el-option v-for="project in filteredProjects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
          <el-input v-model="keyword" class="table-search" placeholder="搜索任务" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        </div>
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="system-card gantt-card">
      <div class="gantt-header">
        <span>任务</span>
        <span>负责人</span>
        <span>周期 / 进度</span>
      </div>
      <div v-for="task in pagedTasks" :key="task.id" class="gantt-row">
        <div class="gantt-task-title">
          <strong>{{ task.title }}</strong>
          <span>{{ task.taskNo }} · {{ statusLabel(task.status) }}</span>
        </div>
        <div class="gantt-owner">{{ task.assigneeName || '未分配' }}</div>
        <div>
          <div class="gantt-range">{{ formatRange(task.startTime, task.dueTime) }}</div>
          <div class="gantt-track">
            <div class="gantt-bar" :style="{ width: `${Number(task.progress || 0)}%` }" />
          </div>
        </div>
      </div>
      <el-empty v-if="!allTasks.length" description="暂无任务" />
      <el-pagination
        v-if="allTasks.length"
        v-model:current-page="page"
        v-model:page-size="size"
        class="table-pagination gantt-pagination"
        layout="total, sizes, prev, pager, next"
        :total="allTasks.length"
      />
    </el-card>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import { projectPageApi, teamPageApi, type ProjectListItem, type TeamItem } from '@/api/project'
import { taskGanttApi, type GanttTaskItem } from '@/api/task'
import { formatDateRange } from '@/utils/format'

const keyword = ref('')
const projectId = ref<number | undefined>()
const teamId = ref<number | undefined>()
const projects = ref<ProjectListItem[]>([])
const allTeams = ref<TeamItem[]>([])
const filteredProjects = computed(() =>
  teamId.value ? projects.value.filter(p => p.teamId === teamId.value) : projects.value
)
const allTasks = ref<GanttTaskItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const pagedTasks = computed(() => {
  const start = (page.value - 1) * size.value
  return allTasks.value.slice(start, start + size.value)
})

onMounted(async () => {
  const [projectResult, teamResult] = await Promise.all([
    projectPageApi({ page: 1, size: 200 }),
    teamPageApi({ page: 1, size: 200, status: 1 })
  ])
  projects.value = projectResult.records
  allTeams.value = teamResult.records
  await loadData()
})

function onTeamChange() {
  projectId.value = undefined
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    page.value = 1
    allTasks.value = await taskGanttApi({ projectId: projectId.value, keyword: keyword.value, teamId: teamId.value })
  } finally {
    loading.value = false
  }
}

function statusLabel(status: string) {
  const map: Record<string, string> = { TODO: '待处理', DOING: '进行中', TESTING: '测试中', DONE: '已完成', CLOSED: '已关闭' }
  return map[status] || status
}

function formatRange(start?: string, end?: string) {
  return formatDateRange(start, end)
}
</script>

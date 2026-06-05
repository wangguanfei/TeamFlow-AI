<template>
  <PageContainer title="甘特图" description="按任务计划周期展示进度和负责人">
    <el-card shadow="never" class="system-card task-filter-card">
      <div class="table-toolbar">
        <div class="task-filter-group">
          <el-select v-model="projectId" class="task-project-select" clearable placeholder="全部项目" @change="loadData">
            <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
          <el-input v-model="keyword" class="table-search" placeholder="搜索任务" clearable :prefix-icon="Search" @keyup.enter="loadData" />
        </div>
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="system-card gantt-card">
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
import { projectPageApi, type ProjectListItem } from '@/api/project'
import { taskGanttApi, type GanttTaskItem } from '@/api/task'
import { formatDateRange } from '@/utils/format'

const keyword = ref('')
const projectId = ref<number | undefined>()
const projects = ref<ProjectListItem[]>([])
const allTasks = ref<GanttTaskItem[]>([])
const page = ref(1)
const size = ref(10)
const pagedTasks = computed(() => {
  const start = (page.value - 1) * size.value
  return allTasks.value.slice(start, start + size.value)
})

onMounted(async () => {
  projects.value = (await projectPageApi({ page: 1, size: 200 })).records
  await loadData()
})

async function loadData() {
  page.value = 1
  allTasks.value = await taskGanttApi({ projectId: projectId.value, keyword: keyword.value })
}

function statusLabel(status: string) {
  const map: Record<string, string> = { TODO: '待处理', DOING: '进行中', TESTING: '测试中', DONE: '已完成', CLOSED: '已关闭' }
  return map[status] || status
}

function formatRange(start?: string, end?: string) {
  return formatDateRange(start, end)
}
</script>

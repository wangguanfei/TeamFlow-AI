<template>
  <PageContainer title="工作台" description="项目协同、任务进度、知识沉淀与 AI 使用概览">
    <template #actions>
      <PermissionButton permission="project:create" type="primary" :icon="Plus" @click="goCreateProject">新建项目</PermissionButton>
    </template>

    <section class="metric-grid">
      <article v-for="card in metricCards" :key="card.label" class="metric-card">
        <div class="metric-card__icon">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="metric-card__body">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.hint }}</small>
        </div>
      </article>
    </section>

    <section class="dashboard-grid">
      <el-card shadow="never" class="panel panel--trend">
        <template #header>
          <div class="panel-title">
            <span>项目趋势</span>
            <el-tag type="success">实时统计</el-tag>
          </div>
        </template>
        <div ref="trendChartRef" class="chart"></div>
      </el-card>

      <el-card shadow="never" class="panel panel--active">
        <template #header>
          <div class="panel-title">
            <span>成员活跃度</span>
          </div>
        </template>
        <div ref="activeChartRef" class="chart"></div>
      </el-card>

      <el-card shadow="never" class="panel panel--todo">
        <template #header>
          <div class="panel-title">
            <span>当前待办</span>
            <el-tag>实时任务</el-tag>
          </div>
        </template>
        <div v-if="todos.length" class="dashboard-todo-list">
          <article v-for="todo in todos" :key="todo.id" class="dashboard-todo-item" :class="todoDueClass(todo.dueTime)">
            <span class="dashboard-todo-item__dot" />
            <div class="dashboard-todo-item__main">
              <div class="dashboard-todo-item__title">
                <strong>{{ todo.title }}</strong>
                <el-tag size="small" effect="plain">{{ statusLabel(todo.status) }}</el-tag>
              </div>
              <span>{{ todo.taskNo }} · {{ todo.projectName || '未归档项目' }}</span>
              <small>{{ todo.assigneeName || '未分配负责人' }}</small>
            </div>
            <div class="dashboard-todo-item__date">{{ formatTodoTime(todo.dueTime) }}</div>
          </article>
        </div>
        <el-empty v-else description="暂无待办任务" />
      </el-card>

      <el-card shadow="never" class="panel panel--ai">
        <template #header>
          <div class="panel-title">
            <span>AI 使用分布</span>
          </div>
        </template>
        <div class="ai-usage-panel">
          <div class="ai-usage-chart-wrap">
            <div ref="aiChartRef" class="chart ai-usage-chart"></div>
          </div>
          <div class="ai-usage-breakdown">
            <div class="ai-usage-total">
              <span>总会话数</span>
              <strong>{{ aiUsageTotal }}</strong>
              <small>按助手模式统计</small>
            </div>
            <div class="ai-usage-list">
              <div v-for="item in aiUsageStats" :key="item.name" class="ai-usage-row">
                <div class="ai-usage-row__head">
                  <span>
                    <i :style="{ background: item.color }" />
                    {{ item.name }}
                  </span>
                  <strong>{{ item.value }} · {{ item.percent }}%</strong>
                </div>
                <div class="ai-usage-row__track">
                  <em :style="{ width: `${item.percent}%`, background: item.color }" />
                </div>
              </div>
              <el-empty v-if="!aiUsageStats.length" description="暂无 AI 使用数据" />
            </div>
          </div>
        </div>
      </el-card>
    </section>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ChatDotRound, Collection, Finished, FolderOpened, Plus, User } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  aiUsageApi,
  dashboardOverviewApi,
  dashboardTodosApi,
  memberActiveApi,
  projectTrendApi,
  type ChartPoint,
  type DashboardOverview,
  type DashboardTodoItem
} from '@/api/dashboard'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const overview = ref<DashboardOverview>({
  userCount: 0,
  projectCount: 0,
  taskCount: 0,
  doneTaskCount: 0,
  knowledgeDocCount: 0,
  aiMessageCount: 0
})
const trendChartRef = ref<HTMLDivElement>()
const activeChartRef = ref<HTMLDivElement>()
const aiChartRef = ref<HTMLDivElement>()
const chartInstances: echarts.ECharts[] = []
const todos = ref<DashboardTodoItem[]>([])
const aiUsagePoints = ref<ChartPoint[]>([])
const aiUsageColors = ['#2563EB', '#7C3AED', '#38BDF8', '#4F46E5', '#10B981']

const metricCards = computed(() => [
  { label: '成员数', value: overview.value.userCount, hint: '已启用账号', icon: User },
  { label: '项目数', value: overview.value.projectCount, hint: '当前团队项目', icon: FolderOpened },
  { label: '任务数', value: overview.value.taskCount, hint: '跨状态任务', icon: Finished },
  { label: '已完成', value: overview.value.doneTaskCount, hint: 'DONE/CLOSED', icon: Finished },
  { label: '知识文档', value: overview.value.knowledgeDocCount, hint: '已沉淀资料', icon: Collection },
  { label: 'AI消息', value: overview.value.aiMessageCount, hint: '助手交互记录', icon: ChatDotRound }
])
const aiUsageTotal = computed(() => aiUsagePoints.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const aiUsageStats = computed(() => {
  const total = aiUsageTotal.value || 1
  return aiUsagePoints.value.map((item, index) => ({
    ...item,
    color: aiUsageColors[index % aiUsageColors.length],
    percent: Math.round((Number(item.value || 0) / total) * 100)
  }))
})

onMounted(async () => {
  overview.value = await dashboardOverviewApi()
  const [trend, active, aiUsage, todoResult] = await Promise.all([projectTrendApi(), memberActiveApi(), aiUsageApi(), dashboardTodosApi()])
  todos.value = todoResult
  aiUsagePoints.value = aiUsage
  await nextTick()
  renderLineChart(trend)
  renderBarChart(active)
  renderPieChart(aiUsage)
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  chartInstances.forEach((chart) => chart.dispose())
})

function renderLineChart(points: ChartPoint[]) {
  if (!trendChartRef.value) return
  const chart = echarts.init(trendChartRef.value)
  chart.setOption({
    color: ['#2563EB'],
    grid: { top: 32, right: 24, bottom: 30, left: 36 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(17, 24, 39, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#FFFFFF' }
    },
    xAxis: {
      type: 'category',
      data: points.map((item) => item.name),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#E5E7EB' } },
      axisTick: { show: false },
      axisLabel: { color: '#6B7280' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#EEF2F7' } },
      axisLabel: { color: '#6B7280' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37, 99, 235, 0.24)' },
            { offset: 1, color: 'rgba(124, 58, 237, 0.02)' }
          ])
        },
        data: points.map((item) => item.value)
      }
    ]
  })
  chartInstances.push(chart)
}

function renderBarChart(points: ChartPoint[]) {
  if (!activeChartRef.value) return
  const chart = echarts.init(activeChartRef.value)
  chart.setOption({
    color: ['#7C3AED'],
    grid: { top: 24, right: 18, bottom: 28, left: 36 },
    tooltip: {
      backgroundColor: 'rgba(17, 24, 39, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#FFFFFF' }
    },
    xAxis: {
      type: 'category',
      data: points.map((item) => item.name),
      axisLine: { lineStyle: { color: '#E5E7EB' } },
      axisTick: { show: false },
      axisLabel: { color: '#6B7280' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#EEF2F7' } },
      axisLabel: { color: '#6B7280' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        borderRadius: [8, 8, 0, 0],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#7C3AED' },
            { offset: 1, color: '#2563EB' }
          ])
        },
        data: points.map((item) => item.value)
      }
    ]
  })
  chartInstances.push(chart)
}

function renderPieChart(points: ChartPoint[]) {
  if (!aiChartRef.value) return
  const chart = echarts.init(aiChartRef.value)
  const total = points.reduce((sum, item) => sum + Number(item.value || 0), 0)
  chart.setOption({
    color: aiUsageColors,
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(17, 24, 39, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#FFFFFF' }
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '42%',
        style: {
          text: String(total),
          fill: '#111827',
          fontSize: 30,
          fontWeight: 800,
          textAlign: 'center'
        }
      },
      {
        type: 'text',
        left: 'center',
        top: '55%',
        style: {
          text: '次会话',
          fill: '#6B7280',
          fontSize: 12,
          fontWeight: 600,
          textAlign: 'center'
        }
      }
    ],
    series: [
      {
        type: 'pie',
        radius: ['58%', '82%'],
        center: ['50%', '52%'],
        avoidLabelOverlap: true,
        data: points,
        itemStyle: { borderColor: '#FFFFFF', borderWidth: 3 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 8,
          itemStyle: { shadowBlur: 16, shadowColor: 'rgba(37, 99, 235, 0.18)' }
        }
      }
    ]
  })
  chartInstances.push(chart)
}

function resizeCharts() {
  chartInstances.forEach((chart) => chart.resize())
}

function statusLabel(status: string) {
  const map: Record<string, string> = { TODO: '待处理', DOING: '进行中', TESTING: '测试中', DONE: '已完成', CLOSED: '已关闭' }
  return map[status] || status
}

function formatTodoTime(value?: string) {
  return value ? formatDateTime(value) : '未设置'
}

function todoDueClass(value?: string) {
  if (!value) {
    return 'is-open'
  }
  return new Date(value).getTime() < Date.now() ? 'is-overdue' : 'is-planned'
}

function goCreateProject() {
  router.push({ path: '/project/list', query: { create: '1' } })
}
</script>

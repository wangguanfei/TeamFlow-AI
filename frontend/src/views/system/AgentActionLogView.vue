<template>
  <PageContainer title="AI助理审计" description="记录 AI 企业助理工具调用、确认与执行结果">
    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input
          v-model="query.username"
          class="table-search"
          placeholder="发起人"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadData"
        />
        <el-input
          v-model="query.toolName"
          class="tool-search"
          placeholder="工具名称"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadData"
        />
        <el-select v-model="query.status" placeholder="执行状态" clearable style="width: 130px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.isWrite" placeholder="操作类型" clearable style="width: 120px">
          <el-option label="写操作" :value="1" />
          <el-option label="只读查询" :value="0" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 360px"
        />
        <el-button :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" row-key="id">
        <el-table-column prop="createdAt" label="调用时间" width="175">
          <template #default="{ row }">{{ formatDatetime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="发起人" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username || `#${row.userId}` }}</template>
        </el-table-column>
        <el-table-column prop="toolLabel" label="工具" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="tool-cell">
              <span>{{ row.toolLabel || row.toolName }}</span>
              <span class="tool-name">{{ row.toolName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="isWrite" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isWrite ? 'warning' : 'info'" size="small">
              {{ row.isWrite ? '写操作' : '只读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="业务对象" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ targetText(row) }}</template>
        </el-table-column>
        <el-table-column prop="confirmedByUsername" label="确认人" width="145" show-overflow-tooltip>
          <template #default="{ row }">{{ row.confirmedByUsername || '—' }}</template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="100">
          <template #default="{ row }">{{ row.durationMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="异常" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'error-text': row.errorMessage }">{{ row.errorMessage || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="详情" width="70" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :total="pageData.total"
        :page-sizes="[10, 20, 50]"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="AI助理审计详情" width="780px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="调用ID">#{{ currentRow?.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(currentRow?.status || '')" size="small">
            {{ statusLabel(currentRow?.status || '') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起人">{{ currentRow?.username || `#${currentRow?.userId}` }}</el-descriptions-item>
        <el-descriptions-item label="确认人">{{ currentRow?.confirmedByUsername || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工具">{{ currentRow?.toolLabel || currentRow?.toolName }}</el-descriptions-item>
        <el-descriptions-item label="工具名">{{ currentRow?.toolName }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ currentRow?.isWrite ? '写操作' : '只读查询' }}</el-descriptions-item>
        <el-descriptions-item label="业务对象">{{ currentRow ? targetText(currentRow) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ currentRow?.sessionId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="消息ID">{{ currentRow?.messageId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="调用时间">{{ formatDatetime(currentRow?.createdAt || '') }}</el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ formatDatetime(currentRow?.confirmedAt || '') }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentRow?.durationMs ?? '—' }} ms</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDatetime(currentRow?.updatedAt || '') }}</el-descriptions-item>
        <el-descriptions-item v-if="currentRow?.errorMessage" label="异常信息" :span="2">
          <span class="error-text">{{ currentRow.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs class="detail-tabs">
        <el-tab-pane label="调用参数">
          <pre class="json-pre">{{ formatJson(currentRow?.argumentsJson) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="执行预览">
          <pre class="json-pre">{{ formatJson(currentRow?.previewJson) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="执行结果">
          <pre class="json-pre">{{ formatJson(currentRow?.resultJson) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import {
  agentActionLogPageApi,
  type AgentActionLogItem,
  type AgentActionLogQueryParams,
} from '@/api/ai'

const loading = ref(false)
const detailVisible = ref(false)
const currentRow = ref<AgentActionLogItem | null>(null)
const timeRange = ref<string[]>([])

const statusOptions = [
  { label: '待确认', value: 'PENDING' },
  { label: '执行中', value: 'RUNNING' },
  { label: '已执行', value: 'EXECUTED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '失败', value: 'FAILED' },
]

const query = reactive<{
  page: number
  size: number
  username: string
  toolName: string
  status: string
  isWrite: number | ''
}>({
  page: 1,
  size: 10,
  username: '',
  toolName: '',
  status: '',
  isWrite: '',
})

const pageData = reactive<{ records: AgentActionLogItem[]; total: number }>({
  records: [],
  total: 0,
})

async function loadData() {
  loading.value = true
  try {
    const params: AgentActionLogQueryParams = { page: query.page, size: query.size }
    if (query.username) params.username = query.username
    if (query.toolName) params.toolName = query.toolName
    if (query.status) params.status = query.status
    if (query.isWrite !== '') params.isWrite = query.isWrite
    if (timeRange.value?.[0]) params.startTime = timeRange.value[0]
    if (timeRange.value?.[1]) params.endTime = timeRange.value[1]

    const res = await agentActionLogPageApi(params)
    pageData.records = res.records
    pageData.total = Number(res.total)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.username = ''
  query.toolName = ''
  query.status = ''
  query.isWrite = ''
  query.page = 1
  timeRange.value = []
  loadData()
}

function showDetail(row: AgentActionLogItem) {
  currentRow.value = row
  detailVisible.value = true
}

function statusLabel(status: string) {
  return statusOptions.find((item) => item.value === status)?.label || status || '—'
}

function statusTagType(status: string) {
  if (status === 'EXECUTED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  if (status === 'PENDING') return 'warning'
  return 'primary'
}

function targetText(row: AgentActionLogItem) {
  if (!row.targetType && !row.targetId) return '—'
  if (!row.targetId) return row.targetType || '—'
  return `${row.targetType || '对象'} #${row.targetId}`
}

function formatDatetime(val?: string) {
  if (!val) return '—'
  return val.replace('T', ' ').slice(0, 19)
}

function formatJson(str?: string) {
  if (!str) return '—'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

onMounted(loadData)
</script>

<style scoped>
.table-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  align-items: center;
}

.table-search {
  width: 150px;
}

.tool-search {
  width: 180px;
}

.table-pagination {
  margin-top: 16px;
  justify-content: flex-end;
  display: flex;
}

.tool-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.tool-name {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-tabs {
  margin-top: 16px;
}

.json-pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 4px;
  max-height: 260px;
  overflow-y: auto;
  margin: 0;
}

.error-text {
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>

<template>
  <PageContainer title="操作日志" description="记录系统所有数据修改操作">
    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input
          v-model="query.username"
          class="table-search"
          placeholder="操作人"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadData"
        />
        <el-select v-model="query.moduleName" placeholder="操作模块" clearable style="width: 140px">
          <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="query.operationType" placeholder="操作类型" clearable style="width: 130px">
          <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
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

      <div class="table-actions">
        <PermissionButton
          permission="system:operlog:delete"
          type="danger"
          :icon="Delete"
          :disabled="!selectedIds.length"
          @click="handleBatchDelete"
        >
          批量删除
        </PermissionButton>
      </div>

      <el-table
        v-loading="loading"
        :data="pageData.records"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="username" label="操作人" width="110" />
        <el-table-column prop="moduleName" label="模块" width="100" />
        <el-table-column prop="operationType" label="操作类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTagColor(row.operationType)" size="small">
              {{ row.operationType || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestMethod" label="方法" width="75" />
        <el-table-column prop="requestUri" label="请求URI" min-width="180" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="IP" width="130" />
        <el-table-column prop="costMs" label="耗时(ms)" width="90">
          <template #default="{ row }">{{ row.costMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="responseStatus" label="状态" width="75">
          <template #default="{ row }">
            <el-tag :type="row.responseStatus === 200 ? 'success' : 'danger'" size="small">
              {{ row.responseStatus === 200 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="175">
          <template #default="{ row }">{{ formatDatetime(row.createdAt) }}</template>
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

    <el-dialog v-model="detailVisible" title="操作详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作人">{{ currentRow?.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ currentRow?.moduleName }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ currentRow?.operationType }}</el-descriptions-item>
        <el-descriptions-item label="HTTP方法">{{ currentRow?.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求URI" :span="2">{{ currentRow?.requestUri }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ currentRow?.clientIp }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentRow?.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentRow?.responseStatus === 200 ? 'success' : 'danger'" size="small">
            {{ currentRow?.responseStatus === 200 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatDatetime(currentRow?.createdAt || '') }}</el-descriptions-item>
        <el-descriptions-item v-if="currentRow?.errorMessage" label="异常信息" :span="2">
          <span class="error-text">{{ currentRow.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentRow?.requestParams" label="请求参数" :span="2">
          <pre class="params-pre">{{ formatJson(currentRow.requestParams) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Delete, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  operationLogPageApi,
  batchDeleteOperationLogApi,
  type OperationLogItem,
} from '@/api/system'

const loading = ref(false)
const selectedIds = ref<number[]>([])
const detailVisible = ref(false)
const currentRow = ref<OperationLogItem | null>(null)
const timeRange = ref<string[]>([])

const moduleOptions = ['用户管理', '角色管理', '权限管理', '菜单管理', '项目管理', '任务管理', '知识库', '文件管理', 'AI助手']
const typeOptions = ['新增', '修改', '删除', '批量删除', '状态变更', '重置密码', '分配角色', '分配权限', '上传', '发布文档', '回滚版本', '删除会话', '批量删除会话']

const query = reactive({
  page: 1,
  size: 10,
  username: '',
  moduleName: '',
  operationType: '',
})

const pageData = reactive<{ records: OperationLogItem[]; total: number }>({
  records: [],
  total: 0,
})

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: query.page, size: query.size }
    if (query.username) params.username = query.username
    if (query.moduleName) params.moduleName = query.moduleName
    if (query.operationType) params.operationType = query.operationType
    if (timeRange.value?.[0]) params.startTime = timeRange.value[0]
    if (timeRange.value?.[1]) params.endTime = timeRange.value[1]

    const res = await operationLogPageApi(params as never)
    pageData.records = res.records
    pageData.total = Number(res.total)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.username = ''
  query.moduleName = ''
  query.operationType = ''
  query.page = 1
  timeRange.value = []
  loadData()
}

function handleSelectionChange(rows: OperationLogItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条日志？`, '提示', { type: 'warning' })
  await batchDeleteOperationLogApi(selectedIds.value)
  ElMessage.success('删除成功')
  loadData()
}

function showDetail(row: OperationLogItem) {
  currentRow.value = row
  detailVisible.value = true
}

function typeTagColor(type: string) {
  if (!type) return 'info'
  if (type.includes('删除')) return 'danger'
  if (type.includes('新增') || type.includes('上传')) return 'success'
  if (type.includes('修改') || type.includes('状态') || type.includes('分配') || type.includes('重置') || type.includes('发布') || type.includes('回滚')) return 'warning'
  return 'primary'
}

function formatDatetime(val: string) {
  if (!val) return '—'
  return val.replace('T', ' ').slice(0, 19)
}

function formatJson(str: string) {
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
  width: 160px;
}
.table-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.table-pagination {
  margin-top: 16px;
  justify-content: flex-end;
  display: flex;
}
.params-pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 8px;
  border-radius: 4px;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}
.error-text {
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>

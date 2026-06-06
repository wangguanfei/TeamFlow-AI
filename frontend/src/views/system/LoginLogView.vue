<template>
  <PageContainer title="登录日志" description="查看系统所有用户的登录记录">
    <el-card shadow="never" class="system-card">
      <div class="table-toolbar">
        <el-input
          v-model="query.username"
          class="table-search"
          placeholder="账号"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadData"
        />
        <el-select v-model="query.status" placeholder="登录状态" clearable style="width: 130px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
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
          permission="system:loginlog:delete"
          type="danger"
          :icon="Delete"
          :disabled="!selectedIds.length"
          @click="handleBatchDelete"
        >
          批量删除
        </PermissionButton>
        <PermissionButton
          permission="system:loginlog:delete"
          type="warning"
          :icon="Timer"
          @click="cleanDialogVisible = true"
        >
          清理历史
        </PermissionButton>
      </div>

      <el-table
        v-loading="loading"
        :data="pageData.records"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="username" label="账号" min-width="110" />
        <el-table-column prop="loginIp" label="IP 地址" width="140" />
        <el-table-column prop="loginLocation" label="登录地点" width="120">
          <template #default="{ row }">{{ row.loginLocation || '—' }}</template>
        </el-table-column>
        <el-table-column prop="browser" label="浏览器" width="100">
          <template #default="{ row }">{{ row.browser || '—' }}</template>
        </el-table-column>
        <el-table-column prop="os" label="操作系统" width="100">
          <template #default="{ row }">{{ row.os || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="登录时间" width="175">
          <template #default="{ row }">{{ formatDatetime(row.createdAt) }}</template>
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

    <el-dialog v-model="cleanDialogVisible" title="清理历史日志" width="400px">
      <el-form label-width="100px">
        <el-form-item label="清理">
          <el-input-number v-model="cleanDays" :min="1" :max="365" />
          <span style="margin-left: 8px">天前的日志</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cleanDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="handleClean">确认清理</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Delete, Refresh, Timer } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { loginLogPageApi, batchDeleteLoginLogApi, cleanLoginLogApi, type LoginLogItem } from '@/api/system'

const loading = ref(false)
const selectedIds = ref<number[]>([])
const cleanDialogVisible = ref(false)
const cleanDays = ref(30)
const timeRange = ref<string[]>([])

const query = reactive({
  page: 1,
  size: 10,
  username: '',
  status: '' as number | '',
})

const pageData = reactive<{ records: LoginLogItem[]; total: number }>({
  records: [],
  total: 0,
})

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: query.page,
      size: query.size,
    }
    if (query.username) params.username = query.username
    if (query.status !== '') params.status = query.status
    if (timeRange.value?.[0]) params.startTime = timeRange.value[0]
    if (timeRange.value?.[1]) params.endTime = timeRange.value[1]

    const res = await loginLogPageApi(params as never)
    pageData.records = res.records
    pageData.total = Number(res.total)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.username = ''
  query.status = ''
  query.page = 1
  timeRange.value = []
  loadData()
}

function handleSelectionChange(rows: LoginLogItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条日志？`, '提示', { type: 'warning' })
  await batchDeleteLoginLogApi(selectedIds.value)
  ElMessage.success('删除成功')
  loadData()
}

async function handleClean() {
  await ElMessageBox.confirm(`确认清理 ${cleanDays.value} 天前的所有登录日志？此操作不可撤销。`, '警告', { type: 'warning' })
  const count = await cleanLoginLogApi(cleanDays.value)
  cleanDialogVisible.value = false
  ElMessage.success(`已清理 ${count} 条历史日志`)
  loadData()
}

function formatDatetime(val: string) {
  if (!val) return '—'
  return val.replace('T', ' ').slice(0, 19)
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
</style>

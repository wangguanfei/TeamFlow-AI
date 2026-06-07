<template>
  <PageContainer title="部署管理">
    <!-- 触发区域 -->
    <el-card class="trigger-card">
      <div class="trigger-inner">
        <div class="trigger-left">
          <div class="trigger-field">
            <span class="field-label">部署目标</span>
            <el-radio-group v-model="triggerForm.target" :disabled="isRunning" size="default">
              <el-radio-button value="all">全量部署</el-radio-button>
              <el-radio-button value="backend">仅后端</el-radio-button>
              <el-radio-button value="frontend">仅前端</el-radio-button>
            </el-radio-group>
          </div>
          <div class="trigger-field">
            <el-switch
              v-model="triggerForm.skipPull"
              :disabled="isRunning"
              active-text="跳过 git pull"
            />
          </div>
        </div>
        <div class="trigger-right">
          <el-tag v-if="isRunning" type="warning" effect="dark" class="running-tag">
            <el-icon class="is-loading"><Loading /></el-icon>
            部署中
          </el-tag>
          <PermissionButton
            permission="system:deploy:exec"
            type="primary"
            :icon="Promotion"
            :loading="isRunning"
            :disabled="isRunning"
            size="large"
            @click="handleTrigger"
          >
            {{ isRunning ? '部署中...' : '立即部署' }}
          </PermissionButton>
        </div>
      </div>
    </el-card>

    <!-- 实时日志 -->
    <el-card v-if="logVisible" class="log-card">
      <template #header>
        <div class="log-header">
          <div class="log-title">
            <el-icon><Document /></el-icon>
            <span>部署日志</span>
          </div>
          <div class="log-controls">
            <el-tag :type="statusTagType" size="small" effect="dark">{{ statusLabel }}</el-tag>
            <span v-if="costMs" class="cost-text">耗时 {{ (costMs / 1000).toFixed(1) }}s</span>
            <el-switch v-model="autoScroll" active-text="自动跟随" size="small" />
            <el-button text :icon="Close" size="small" @click="closeLog">关闭</el-button>
          </div>
        </div>
      </template>
      <pre ref="logEl" class="log-panel">{{ logContent }}</pre>
    </el-card>

    <!-- 历史记录 -->
    <el-card class="history-card">
      <template #header>
        <div class="history-header">
          <div class="history-title">
            <el-icon><Timer /></el-icon>
            <span>部署历史</span>
          </div>
          <div class="history-stats">
            <span class="stat-item">
              共 <strong>{{ total }}</strong> 次
            </span>
            <span class="stat-item success">
              成功 <strong>{{ successCount }}</strong>
            </span>
            <span class="stat-item danger">
              失败 <strong>{{ failedCount }}</strong>
            </span>
          </div>
        </div>
      </template>

      <el-table :data="records" v-loading="loading" stripe size="small" style="width: 100%">
        <el-table-column prop="startedAt" label="时间" width="175">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="triggerUsername" label="触发人" min-width="120" />
        <el-table-column prop="target" label="目标" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ targetLabel(row.target) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="recordTagType(row.status)" size="small" effect="dark">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时" width="100">
          <template #default="{ row }">
            {{ row.costMs != null ? (row.costMs / 1000).toFixed(1) + 's' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="viewLog(row.id)">查看日志</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="20"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadHistory"
        />
      </div>
    </el-card>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Promotion, Close, Document, Timer } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  triggerDeployApi,
  currentDeployApi,
  deployPageApi,
  deployLogStreamApi,
  type DeployRecordItem,
  type DeployStatus,
  type DeployTarget,
} from '@/api/deploy'

const triggerForm = ref({ target: 'all' as DeployTarget, skipPull: false })
const isRunning = ref(false)
const records = ref<DeployRecordItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const total = ref(0)

const logVisible = ref(false)
const logContent = ref('')
const logStatus = ref<DeployStatus | ''>('')
const costMs = ref<number | null>(null)
const autoScroll = ref(true)
const logEl = ref<HTMLPreElement | null>(null)

let abortController: AbortController | null = null
let userAborted = false

const successCount = computed(() => records.value.filter(r => r.status === 'SUCCESS').length)
const failedCount = computed(() => records.value.filter(r => r.status === 'FAILED').length)

const statusLabel = computed(() => {
  if (!logStatus.value) return '运行中'
  return logStatus.value === 'SUCCESS' ? '成功' : logStatus.value === 'FAILED' ? '失败' : '运行中'
})

const statusTagType = computed(() => {
  if (logStatus.value === 'SUCCESS') return 'success'
  if (logStatus.value === 'FAILED') return 'danger'
  return 'warning'
})

function recordTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

function statusText(status: string) {
  return status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : '运行中'
}

function targetLabel(target: string) {
  return target === 'all' ? '全量' : target === 'backend' ? '后端' : '前端'
}

function formatDateTime(s: string | null) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 19)
}

function scrollToBottom() {
  if (autoScroll.value && logEl.value) {
    nextTick(() => {
      if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
    })
  }
}

function appendLog(line: string) {
  const MAX_LINES = 2000
  const lines = logContent.value.split('\n')
  if (lines.length > MAX_LINES) lines.splice(0, lines.length - MAX_LINES)
  logContent.value = lines.join('\n') + (logContent.value ? '\n' : '') + line
  scrollToBottom()
}

async function startStream(deployId: number) {
  userAborted = false
  logVisible.value = true
  logStatus.value = ''

  const MAX_RETRIES = 20
  const RETRY_DELAY = 5000

  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    if (userAborted) break

    abortController?.abort()
    abortController = new AbortController()
    const signal = abortController.signal
    let doneReceived = false

    try {
      await deployLogStreamApi(
        deployId,
        (line) => appendLog(line),
        (status, _exitCode, ms) => {
          doneReceived = true
          logStatus.value = status
          costMs.value = ms
          isRunning.value = false
          loadHistory().catch(() => {/* ignore if backend briefly unavailable */})
        },
        (_msg) => {
          // 502/503 等错误不弹 toast，走重连流程
        },
        signal
      )
    } catch (e: unknown) {
      if (e instanceof Error && e.name === 'AbortError') return
    }

    if (doneReceived || userAborted) break

    // 连接断开但没收到 done，检查后端是否还在部署
    try {
      const current = await currentDeployApi(true) // silent=true，后端重启期间不弹错误
      if (current.running && current.deployId === deployId) {
        const waitSec = Math.round(RETRY_DELAY / 1000)
        appendLog(`\n[连接中断，${waitSec}s 后重连... (${attempt + 1}/${MAX_RETRIES})]`)
        await new Promise<void>((resolve) => {
          const t = setTimeout(resolve, RETRY_DELAY)
          signal.addEventListener('abort', () => { clearTimeout(t); resolve() })
        })
        if (userAborted) break
        appendLog('[重新连接...]')
        continue
      }
    } catch {
      // 后端还没起来，继续等（静默，不弹 toast）
      if (attempt < MAX_RETRIES) {
        appendLog(`\n[后端重启中，${Math.round(RETRY_DELAY / 1000)}s 后重连... (${attempt + 1}/${MAX_RETRIES})]`)
        await new Promise<void>((r) => setTimeout(r, RETRY_DELAY))
        continue
      }
    }

    isRunning.value = false
    loadHistory().catch(() => {/* ignore */})
    break
  }
}

async function handleTrigger() {
  try {
    await ElMessageBox.confirm(
      `确认部署目标：${targetLabel(triggerForm.value.target)}${triggerForm.value.skipPull ? '（跳过 git pull）' : ''}？`,
      '确认部署',
      { type: 'warning', confirmButtonText: '开始部署', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    logContent.value = ''
    costMs.value = null
    const deployId = await triggerDeployApi(triggerForm.value)
    isRunning.value = true
    ElMessage.success('部署已启动')
    await startStream(deployId)
  } catch {
    // error message shown by http interceptor
  }
}

async function viewLog(deployId: number) {
  logContent.value = ''
  costMs.value = null
  logStatus.value = ''
  await startStream(deployId)
}

function closeLog() {
  userAborted = true
  abortController?.abort()
  logVisible.value = false
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await deployPageApi({ page: currentPage.value, size: 20 })
    records.value = res.records
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}

async function checkRunning() {
  try {
    const res = await currentDeployApi(true)
    if (res.running && res.deployId) {
      isRunning.value = true
      ElMessage.info('检测到进行中的部署，自动连接日志...')
      await startStream(res.deployId)
    }
  } catch {
    // ignore
  }
}

onMounted(async () => {
  await loadHistory()
  await checkRunning()
})

onUnmounted(() => {
  userAborted = true
  abortController?.abort()
})
</script>

<style scoped>
/* ── 触发卡片 ── */
.trigger-card {
  margin-bottom: 16px;
}

.trigger-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.trigger-left {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.trigger-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.trigger-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.running-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ── 日志卡片 ── */
.log-card {
  margin-bottom: 16px;
}

.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.log-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.log-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cost-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.log-panel {
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 12px 16px;
  border-radius: 6px;
  height: 420px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

/* ── 历史卡片 ── */
.history-card {
  /* fills remaining space */
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.history-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.history-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.stat-item strong {
  color: var(--el-text-color-primary);
}

.stat-item.success strong {
  color: var(--el-color-success);
}

.stat-item.danger strong {
  color: var(--el-color-danger);
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

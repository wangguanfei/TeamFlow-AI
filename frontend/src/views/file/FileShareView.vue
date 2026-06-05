<template>
  <div class="share-page">
    <div class="share-card">
      <header class="share-card__head">
        <div class="share-brand">
          <span class="share-brand__logo">TF</span>
          <span class="share-brand__name">TeamFlow 文件分享</span>
        </div>
        <el-tag v-if="share" type="success" effect="light" round>分享有效</el-tag>
      </header>

      <div v-if="loading" v-loading="true" class="share-state" element-loading-text="正在校验分享链接…" />

      <el-result
        v-else-if="errorMessage"
        icon="warning"
        title="无法访问该分享"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" @click="loadShare">重试</el-button>
        </template>
      </el-result>

      <template v-else-if="share">
        <div class="share-file">
          <span class="share-file__icon"><el-icon><Document /></el-icon></span>
          <div class="share-file__meta">
            <strong :title="share.fileName">{{ share.fileName || '未命名文件' }}</strong>
            <span>分享码 {{ share.shareCode }} · 有效期至 {{ formatDate(share.expireTime) }}</span>
          </div>
          <el-button type="primary" :icon="Download" :loading="downloading" @click="download">下载</el-button>
        </div>

        <div class="share-preview" v-loading="previewing" element-loading-text="正在生成预览…">
          <img v-if="previewMode === 'image'" :src="previewUrl" alt="文件预览" />
          <iframe v-else-if="previewMode === 'pdf'" :src="previewUrl" />
          <pre v-else-if="previewMode === 'text'">{{ previewText }}</pre>
          <VueOfficeDocx v-else-if="previewMode === 'word'" :src="previewSrc" class="share-preview__office" />
          <VueOfficeExcel v-else-if="previewMode === 'excel'" :src="previewSrc" class="share-preview__sheet" />
          <VueOfficePptx v-else-if="previewMode === 'pptx'" :src="previewSrc" class="share-preview__office" />
          <el-empty v-else description="该类型暂不支持在线预览（如旧版 doc/xls/ppt），请下载后查看" />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Document, Download } from '@element-plus/icons-vue'
import { fileShareAccessApi, sharedDownloadBlobApi, sharedPreviewBlobApi, type FileShareItem } from '@/api/file'
import { resolvePreview, type PreviewMode } from '@/utils/filePreview'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import VueOfficePptx from '@vue-office/pptx'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const shareCode = String(route.params.code || '')

const loading = ref(true)
const previewing = ref(false)
const downloading = ref(false)
const errorMessage = ref('')
const share = ref<FileShareItem | null>(null)
const previewMode = ref<PreviewMode>('none')
const previewUrl = ref('')
const previewText = ref('')
const previewSrc = ref<ArrayBuffer>()

onMounted(loadShare)
onBeforeUnmount(releasePreview)

async function loadShare() {
  if (!shareCode) {
    errorMessage.value = '分享码无效'
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    share.value = await fileShareAccessApi(shareCode)
    await buildPreview()
  } catch (error) {
    errorMessage.value = resolveShareError(error)
  } finally {
    loading.value = false
  }
}

// 把 axios 抛出的技术错误（如 "Request failed with status code 400"）
// 转换为后端返回的业务文案（如"分享链接已过期"），避免向用户暴露专业提示词
function resolveShareError(error: unknown) {
  if (axios.isAxiosError(error)) {
    const backendMessage = (error.response?.data as { message?: string } | undefined)?.message
    if (backendMessage) {
      return backendMessage
    }
    if (error.response?.status === 404) {
      return '分享链接不存在'
    }
    if (error.code === 'ECONNABORTED' || error.message === 'Network Error') {
      return '网络连接超时，请稍后重试'
    }
  }
  return '分享链接不存在或已过期'
}

async function buildPreview() {
  if (!share.value) return
  previewing.value = true
  try {
    releasePreview()
    const response = await sharedPreviewBlobApi(shareCode)
    const blob = response.data
    const ext = share.value.fileName?.split('.').pop()?.toLowerCase()
    const result = await resolvePreview(blob, blob.type, ext)
    previewMode.value = result.mode
    previewUrl.value = result.url || ''
    previewText.value = result.text || ''
    previewSrc.value = result.src
  } catch {
    previewMode.value = 'none'
  } finally {
    previewing.value = false
  }
}

async function download() {
  if (downloading.value || !share.value) return
  downloading.value = true
  try {
    const response = await sharedDownloadBlobApi(shareCode)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = share.value.fileName || 'download'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('文件下载失败，请稍后重试')
  } finally {
    downloading.value = false
  }
}

function releasePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewText.value = ''
  previewSrc.value = undefined
  previewMode.value = 'none'
}

function formatDate(value?: string) {
  return formatDateTime(value)
}
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 16px;
  background: radial-gradient(circle at 20% 0%, #eef4ff 0%, #f8fafc 45%, #f1f5f9 100%);
}

.share-card {
  width: min(1200px, 100%);
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 20px;
  box-shadow: 0 24px 60px -32px rgba(15, 23, 42, 0.4);
  overflow: hidden;
}

.share-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 28px;
  border-bottom: 1px solid #f1f5f9;
}

.share-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.share-brand__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
  font-weight: 800;
  letter-spacing: 1px;
}

.share-brand__name {
  font-weight: 700;
  color: #0f172a;
}

.share-state {
  min-height: 240px;
}

.share-file {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 28px;
}

.share-file__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #eef6ff;
  color: #2563eb;
  font-size: 24px;
  flex: 0 0 auto;
}

.share-file__meta {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 4px;
}

.share-file__meta strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-file__meta span {
  color: #64748b;
  font-size: 13px;
}

.share-preview {
  height: 78vh;
  display: flex;
  flex-direction: column;
  margin: 0 28px 28px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #f8fafc;
}

.share-preview img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  margin: auto;
  object-fit: contain;
  border-radius: 12px;
}

.share-preview iframe {
  flex: 1;
  width: 100%;
  border: none;
  border-radius: 12px;
}

.share-preview pre {
  flex: 1;
  overflow: auto;
  margin: 0;
  padding: 8px 4px;
  color: #1f2937;
  white-space: pre-wrap;
}

.share-preview__office {
  flex: 1;
  overflow: auto;
  background: #fff;
  border-radius: 12px;
}

/* Excel：固定高度并由组件内部滚动，避免被压扁 */
.share-preview__sheet {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

.share-preview__sheet :deep(.vue-office-excel),
.share-preview__sheet :deep(.vue-office-excel-main) {
  height: 100%;
}
</style>

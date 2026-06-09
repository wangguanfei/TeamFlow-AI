<template>
  <PageContainer title="文件中心" description="统一管理项目、任务、知识库相关文件，支持上传、预览、下载和分享">
    <template #actions>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      <PermissionButton permission="file:upload" type="primary" :icon="UploadFilled" @click="openUpload">上传文件</PermissionButton>
    </template>

    <div class="file-stat-grid">
      <div class="file-stat-card">
        <span>文件总数</span>
        <strong>{{ pageData.total }}</strong>
        <small>当前筛选范围</small>
      </div>
      <div class="file-stat-card">
        <span>当前页容量</span>
        <strong>{{ formatSize(currentPageSize) }}</strong>
        <small>文件资产统计</small>
      </div>
      <div class="file-stat-card">
        <span>图片文件</span>
        <strong>{{ imageCount }}</strong>
        <small>支持在线预览</small>
      </div>
      <div class="file-stat-card">
        <span>分享记录</span>
        <strong>{{ shareData.total }}</strong>
        <small>7 天默认有效期</small>
      </div>
    </div>

    <el-card shadow="never" class="system-card file-table-card">
      <div class="table-toolbar">
        <div class="file-filter-group">
          <el-input v-model="keyword" class="table-search" placeholder="搜索文件名、扩展名或业务类型" clearable :prefix-icon="Search" @keyup.enter="loadData" />
          <el-select v-model="bizType" class="file-type-select" clearable placeholder="业务类型" @change="loadData">
            <el-option label="通用文件" value="COMMON" />
            <el-option label="项目文件" value="PROJECT" />
            <el-option label="任务附件" value="TASK" />
            <el-option label="知识库素材" value="KNOWLEDGE" />
          </el-select>
        </div>
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" row-key="id">
        <el-table-column label="文件" min-width="300">
          <template #default="{ row }">
            <div class="file-name-cell">
              <span class="file-type-icon" :class="fileIconClass(row)">
                <el-icon><component :is="fileIcon(row)" /></el-icon>
              </span>
              <div class="file-name-cell__meta">
                <strong>{{ row.originalName }}</strong>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="业务归档" min-width="160">
          <template #default="{ row }">
            <el-tag>{{ bizTypeLabel(row.bizType) }}</el-tag>
            <span v-if="row.bizId" class="file-biz-id">#{{ row.bizId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="130" />
        <el-table-column label="上传时间" min-width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="previewingId === row.id" @click="previewFile(row)">预览</el-button>
            <el-button link type="primary" :loading="downloadingId === row.id" @click="downloadFile(row)">下载</el-button>
            <PermissionButton permission="file:share" link type="primary" :loading="sharingId === row.id" @click="shareFile(row)">分享</PermissionButton>
            <PermissionButton permission="file:update" link type="primary" @click="openEdit(row)">归档</PermissionButton>
            <PermissionButton permission="file:delete" link type="danger" @click="removeFile(row)">删除</PermissionButton>
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

    <el-dialog v-model="uploadVisible" title="上传文件" width="620px">
      <el-form :model="uploadForm" label-width="88px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="业务类型">
              <el-select v-model="uploadForm.bizType">
                <el-option label="通用文件" value="COMMON" />
                <el-option label="项目文件" value="PROJECT" />
                <el-option label="任务附件" value="TASK" />
                <el-option label="知识库素材" value="KNOWLEDGE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="!isKnowledgeImportUpload" label="业务ID">
              <el-input-number v-model="uploadForm.bizId" :min="0" controls-position="right" />
            </el-form-item>
            <el-form-item v-else label="知识空间">
              <el-select v-model="uploadForm.spaceId" placeholder="选择知识空间">
                <el-option v-for="space in knowledgeSpaces" :key="space.id" :label="space.spaceName" :value="space.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <template v-if="uploadForm.bizType === 'KNOWLEDGE'">
          <el-form-item label="导入文档">
            <el-switch v-model="uploadForm.importAsKnowledgeDoc" />
          </el-form-item>
          <el-row v-if="uploadForm.importAsKnowledgeDoc" :gutter="16">
            <el-col :span="12">
              <el-form-item label="文档标题">
                <el-input v-model="uploadForm.title" placeholder="单文件可指定标题，多文件默认使用文件名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="自动发布">
                <el-switch v-model="uploadForm.autoPublish" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item v-if="uploadForm.importAsKnowledgeDoc" label="标签">
            <el-input v-model="uploadForm.tagsText" placeholder="多个标签用逗号分隔" />
          </el-form-item>
        </template>
        <el-upload
          v-model:file-list="uploadFileList"
          drag
          multiple
          action="#"
          :http-request="uploadRequest"
          :show-file-list="true"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽一个或多个文件到此处，或 <em>点击上传</em></div>
        </el-upload>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="业务归档" width="520px">
      <el-alert
        class="archive-tip"
        type="info"
        :closable="false"
        show-icon
        title="归档用于把文件归类到具体业务"
        description="选择业务类型（项目/任务/知识库）并填写对应业务ID后，该文件即与目标业务关联，可在对应模块按业务检索、统计与筛选；也可在此重命名文件。"
      />
      <el-form :model="editForm" label-width="88px">
        <el-form-item label="文件名">
          <el-input v-model="editForm.originalName" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="editForm.bizType">
            <el-option label="通用文件" value="COMMON" />
            <el-option label="项目文件" value="PROJECT" />
            <el-option label="任务附件" value="TASK" />
            <el-option label="知识库素材" value="KNOWLEDGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input-number v-model="editForm.bizId" :min="0" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveArchive">保存归档</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      width="90%"
      top="5vh"
      class="file-preview-dialog"
      append-to-body
      destroy-on-close
      @opened="previewReady = true"
      @closed="releasePreviewUrl"
    >
      <template #header>
        <div v-if="previewTarget" class="file-preview__header">
          <strong>{{ previewTarget.originalName }}</strong>
          <span>{{ formatSize(previewTarget.fileSize) }}</span>
        </div>
      </template>
      <div v-if="previewTarget" class="file-preview">
        <img v-if="previewMode === 'image'" :src="previewUrl" alt="文件预览" />
        <pre v-else-if="previewMode === 'text'">{{ previewText }}</pre>
        <iframe v-else-if="previewMode === 'pdf'" :src="previewUrl" />
        <VueOfficeDocx v-else-if="previewMode === 'word' && previewReady" :src="previewSrc" class="file-preview__office" />
        <VueOfficeExcel v-else-if="previewMode === 'excel' && previewReady" :src="previewSrc" class="file-preview__sheet" />
        <VueOfficePptx v-else-if="previewMode === 'pptx' && previewReady" :src="previewSrc" class="file-preview__office" />
        <el-empty v-else-if="previewMode === 'none'" description="该类型暂不支持在线预览（如旧版 doc/xls/ppt），请下载查看" />
      </div>
    </el-dialog>

    <el-dialog v-model="shareVisible" title="文件分享" width="520px">
      <div v-if="shareResult" class="file-share-result">
        <span>分享码</span>
        <strong>{{ shareResult.shareCode }}</strong>
        <small>有效期至 {{ formatDate(shareResult.expireTime) }}</small>
        <div class="file-share-link">
          <el-input :model-value="shareUrl" readonly />
          <el-button type="primary" :icon="CopyDocument" @click="copyShare">复制链接</el-button>
        </div>
        <small class="file-share-tip">任何人通过该链接即可在有效期内查看与下载，同一文件重复分享会复用此链接。</small>
      </div>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions, type UploadUserFile } from 'element-plus'
import { CopyDocument, Document, Files, Picture, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  createFileShareApi,
  deleteFileApi,
  downloadFileBlobApi,
  filePageApi,
  fileSharePageApi,
  previewFileBlobApi,
  updateFileApi,
  uploadFileApi,
  type FileItem,
  type FileShareItem
} from '@/api/file'
import { importKnowledgeDocFileApi, knowledgeSpacePageApi, type KnowledgeSpaceItem } from '@/api/knowledge'
import { resolvePreview, type PreviewMode } from '@/utils/filePreview'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import VueOfficePptx from '@vue-office/pptx'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const keyword = ref('')
const bizType = ref('')
const page = ref(1)
const size = ref(10)
const pageData = reactive({ page: 1, size: 10, total: 0, records: [] as FileItem[] })
const shareData = reactive({ page: 1, size: 10, total: 0, records: [] as FileShareItem[] })
const uploadVisible = ref(false)
const editVisible = ref(false)
const previewVisible = ref(false)
const shareVisible = ref(false)
const previewTarget = ref<FileItem | null>(null)
const previewingId = ref<number | null>(null)
const downloadingId = ref<number | null>(null)
const sharingId = ref<number | null>(null)
const previewUrl = ref('')
const previewText = ref('')
const previewSrc = ref<ArrayBuffer>()
const previewMode = ref<PreviewMode>('none')
// Office 渲染器按容器宽度计算尺寸，需等对话框打开动画结束、宽度就绪后再挂载
const previewReady = ref(false)
const shareResult = ref<FileShareItem | null>(null)
const editingId = ref<number | null>(null)
const knowledgeSpaces = ref<KnowledgeSpaceItem[]>([])
const uploadFileList = ref<UploadUserFile[]>([])

const uploadForm = reactive({
  bizType: 'COMMON',
  bizId: 0,
  importAsKnowledgeDoc: true,
  spaceId: undefined as number | undefined,
  title: '',
  tagsText: '',
  autoPublish: true
})

const editForm = reactive({
  originalName: '',
  bizType: 'COMMON',
  bizId: 0
})

const currentPageSize = computed(() => pageData.records.reduce((sum, item) => sum + Number(item.fileSize || 0), 0))
const imageCount = computed(() => pageData.records.filter((item) => item.contentType?.startsWith('image/')).length)
const shareUrl = computed(() => shareResult.value ? `${window.location.origin}/file/share/${shareResult.value.shareCode}` : '')
const isKnowledgeImportUpload = computed(() => uploadForm.bizType === 'KNOWLEDGE' && uploadForm.importAsKnowledgeDoc)

onMounted(() => {
  loadData()
  loadKnowledgeSpaces()
})

async function loadData() {
  loading.value = true
  try {
    const [files, shares] = await Promise.all([
      filePageApi({ page: page.value, size: size.value, keyword: keyword.value, bizType: bizType.value || undefined }),
      fileSharePageApi({ page: 1, size: 10 })
    ])
    Object.assign(pageData, files)
    Object.assign(shareData, shares)
  } finally {
    loading.value = false
  }
}

function openUpload() {
  if (!uploadForm.spaceId) {
    uploadForm.spaceId = knowledgeSpaces.value[0]?.id
  }
  uploadFileList.value = []
  uploadVisible.value = true
}

async function uploadRequest(options: UploadRequestOptions) {
  try {
    const file = options.file as File
    const result = isKnowledgeImportUpload.value
      ? await importKnowledgeFile(file)
      : await uploadFileApi(file, {
          bizType: uploadForm.bizType,
          bizId: uploadForm.bizId || undefined
        })
    options.onSuccess?.(result)
    ElMessage.success(isKnowledgeImportUpload.value ? `${file.name} 已导入知识库` : `${file.name} 上传成功`)
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
    throw error
  }
}

async function importKnowledgeFile(file: File) {
  if (!uploadForm.spaceId) {
    throw new Error('请选择知识空间')
  }
  const result = await importKnowledgeDocFileApi(file, {
    spaceId: uploadForm.spaceId,
    title: uploadFileList.value.length <= 1 ? uploadForm.title.trim() || undefined : undefined,
    tags: parseTags(uploadForm.tagsText),
    autoPublish: uploadForm.autoPublish
  })
  return result.file
}

async function loadKnowledgeSpaces() {
  const result = await knowledgeSpacePageApi({ page: 1, size: 100 })
  knowledgeSpaces.value = result.records
  if (!uploadForm.spaceId) {
    uploadForm.spaceId = knowledgeSpaces.value[0]?.id
  }
}

function openEdit(row: FileItem) {
  editingId.value = row.id
  Object.assign(editForm, {
    originalName: row.originalName,
    bizType: row.bizType || 'COMMON',
    bizId: row.bizId || 0
  })
  editVisible.value = true
}

async function saveArchive() {
  if (!editingId.value || !editForm.originalName.trim()) {
    ElMessage.warning('请填写文件名')
    return
  }
  await updateFileApi(editingId.value, {
    originalName: editForm.originalName.trim(),
    bizType: editForm.bizType,
    bizId: editForm.bizId || undefined
  })
  ElMessage.success('归档信息已保存')
  editVisible.value = false
  await loadData()
}

async function previewFile(row: FileItem) {
  if (previewingId.value) return
  previewingId.value = row.id
  try {
    releasePreviewUrl()
    previewTarget.value = row
    const response = await previewFileBlobApi(row.id)
    const result = await resolvePreview(response.data, row.contentType, row.fileExt)
    previewMode.value = result.mode
    previewUrl.value = result.url || ''
    previewText.value = result.text || ''
    previewSrc.value = result.src
    previewVisible.value = true
  } catch {
    ElMessage.error('文件预览失败，请稍后重试')
  } finally {
    previewingId.value = null
  }
}

async function downloadFile(row: FileItem) {
  if (downloadingId.value) return
  downloadingId.value = row.id
  try {
    const response = await downloadFileBlobApi(row.id)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = row.originalName
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('文件下载失败，请稍后重试')
  } finally {
    downloadingId.value = null
  }
}

async function shareFile(row: FileItem) {
  if (sharingId.value) return
  sharingId.value = row.id
  try {
    shareResult.value = await createFileShareApi({ fileId: row.id, expireDays: 7 })
    shareVisible.value = true
    await loadData()
  } finally {
    sharingId.value = null
  }
}

async function removeFile(row: FileItem) {
  await ElMessageBox.confirm(`确认删除文件「${row.originalName}」？`, '删除文件', { type: 'warning' })
  await deleteFileApi(row.id)
  ElMessage.success('文件已删除')
  await loadData()
}

async function copyShare() {
  const text = shareUrl.value
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
    ElMessage.success('分享链接已复制')
  } catch {
    fallbackCopy(text)
    ElMessage.success('分享链接已复制')
  }
}

function fallbackCopy(text: string) {
  // 非安全上下文（http/局域网 IP）下 navigator.clipboard 不可用，退回 execCommand
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

function releasePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewText.value = ''
  previewSrc.value = undefined
  previewMode.value = 'none'
  previewReady.value = false
}

function fileIcon(row: FileItem) {
  if (row.contentType?.startsWith('image/')) return Picture
  if (['pdf', 'doc', 'docx', 'md', 'txt'].includes(row.fileExt || '')) return Document
  return Files
}

function fileIconClass(row: FileItem) {
  if (row.contentType?.startsWith('image/')) return 'is-image'
  if (['pdf', 'doc', 'docx', 'md', 'txt'].includes(row.fileExt || '')) return 'is-doc'
  return 'is-file'
}

function bizTypeLabel(value: string) {
  const map: Record<string, string> = {
    COMMON: '通用',
    PROJECT: '项目',
    TASK: '任务',
    KNOWLEDGE: '知识库'
  }
  return map[value] || value
}

function parseTags(value: string) {
  return value
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function formatSize(value?: number) {
  const sizeValue = Number(value || 0)
  if (sizeValue < 1024) return `${sizeValue} B`
  if (sizeValue < 1024 * 1024) return `${(sizeValue / 1024).toFixed(1)} KB`
  if (sizeValue < 1024 * 1024 * 1024) return `${(sizeValue / 1024 / 1024).toFixed(1)} MB`
  return `${(sizeValue / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function formatDate(value?: string) {
  return formatDateTime(value)
}
</script>

<style scoped>
.file-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.file-stat-card {
  padding: 18px 20px;
  border: 1px solid rgba(229, 231, 235, 0.9);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--tf-shadow);
}

.file-stat-card span,
.file-stat-card small {
  display: block;
  color: var(--tf-muted);
}

.file-stat-card span {
  font-weight: 760;
}

.file-stat-card strong {
  display: block;
  margin: 6px 0 4px;
  color: var(--tf-text);
  font-size: 28px;
  line-height: 1.1;
}

.file-filter-group {
  display: flex;
  flex: 1;
  gap: 12px;
  align-items: center;
}

.file-type-select {
  width: 160px;
}

.file-name-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-name-cell__meta {
  min-width: 0;
}

.file-name-cell__meta strong,
.file-name-cell__meta span {
  display: block;
}

.file-name-cell__meta strong {
  overflow: hidden;
  color: var(--tf-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-name-cell__meta span {
  overflow-wrap: anywhere;
  color: var(--tf-muted);
  font-size: 12px;
}

.file-type-icon {
  position: relative;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 12px;
  background: #eef6ff;
  color: var(--tf-primary);
  font-size: 20px;
}

.file-type-icon .el-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  line-height: 1;
  transform: translate(-50%, -50%);
}

.file-type-icon .el-icon svg {
  display: block;
}

.file-type-icon :deep(svg) {
  width: 20px;
  height: 20px;
  display: block;
}

.file-type-icon.is-image {
  background: #ecfdf5;
  color: var(--tf-success);
}

.file-type-icon.is-doc {
  background: #f5f3ff;
  color: var(--tf-secondary);
}

.file-biz-id {
  display: inline-block;
  overflow: hidden;
  max-width: 120px;
  margin-left: 6px;
  color: var(--tf-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.file-preview-dialog :deep(.el-dialog__body) {
  padding: 0 20px 20px;
}

.file-preview__header {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.file-preview__header strong {
  overflow: hidden;
  color: var(--tf-text);
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-preview__header span {
  color: var(--tf-muted);
  font-size: 12px;
}

/* 预览主体固定高度，给 Office 渲染器一个确定的可计算高度 */
.file-preview {
  height: 82vh;
  display: flex;
  flex-direction: column;
}

.file-preview img {
  max-width: 100%;
  max-height: 100%;
  margin: auto;
  object-fit: contain;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
}

.file-preview pre {
  flex: 1;
  overflow: auto;
  margin: 0;
  padding: 16px;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  background: #f8fafc;
  color: #374151;
  white-space: pre-wrap;
}

.file-preview iframe {
  flex: 1;
  width: 100%;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
}

/* Word/PPT：内容自撑高，父容器滚动 */
.file-preview__office {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  background: #fff;
}

/* Excel：必须给确定高度，由 @vue-office/excel 在内部自行滚动，
   外层不能用 overflow:auto 否则会反复重算导致表格被压扁 */
.file-preview__sheet {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--tf-border);
  border-radius: 14px;
  background: #fff;
}

.file-preview__sheet :deep(.vue-office-excel),
.file-preview__sheet :deep(.vue-office-excel-main) {
  height: 100%;
}

.archive-tip {
  margin-bottom: 16px;
}

.file-share-link {
  display: flex;
  gap: 10px;
}

.file-share-link .el-input {
  flex: 1;
}

.file-share-tip {
  color: var(--tf-muted);
  font-size: 12px;
  line-height: 1.6;
}

.file-share-result {
  display: grid;
  gap: 10px;
}

.file-share-result span,
.file-share-result small {
  color: var(--tf-muted);
}

.file-share-result strong {
  color: var(--tf-primary);
  font-size: 28px;
  letter-spacing: 2px;
}

@media (max-width: 1100px) {
  .file-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .file-stat-grid {
    grid-template-columns: 1fr;
  }

  .file-filter-group {
    flex-direction: column;
    align-items: stretch;
  }

  .file-type-select {
    width: 100%;
  }
}
</style>

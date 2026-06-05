<template>
  <el-drawer v-model="visible" title="任务详情" size="560px">
    <div v-if="detail" class="task-detail">
      <div class="task-detail__header">
        <span>{{ detail.task.taskNo }}</span>
        <h3>{{ detail.task.title }}</h3>
        <p>{{ detail.task.description || '暂无描述' }}</p>
      </div>

      <div class="detail-grid">
        <span>项目</span><strong>{{ detail.task.projectName || '-' }}</strong>
        <span>负责人</span>
        <div class="task-assignee-editor">
          <el-select v-model="assigneeId" filterable placeholder="选择负责人" size="small">
            <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
          </el-select>
          <PermissionButton permission="task:update" type="primary" size="small" :loading="assigning" @click="assignTask">保存</PermissionButton>
        </div>
        <span>执行人</span>
        <div class="task-assignee-editor">
          <el-select v-model="executorIds" filterable multiple collapse-tags collapse-tags-tooltip placeholder="选择一个或多个执行人" size="small">
            <el-option v-for="user in users" :key="user.id" :label="displayUser(user)" :value="user.id" />
          </el-select>
          <PermissionButton permission="task:update" type="primary" size="small" :loading="savingExecutors" @click="saveExecutors">保存</PermissionButton>
        </div>
        <span>报告人</span><strong>{{ detail.task.reporterName || '-' }}</strong>
        <span>状态</span><strong>{{ statusLabel(detail.task.status) }}</strong>
        <span>优先级</span><strong>{{ priorityLabel(detail.task.priority) }}</strong>
        <span>工时</span><strong>{{ detail.task.actualHours || 0 }}h / {{ detail.task.estimateHours || 0 }}h</strong>
      </div>

      <el-tabs class="task-detail-tabs">
        <el-tab-pane label="评论">
          <div class="inline-form">
            <el-input v-model="commentText" type="textarea" :rows="2" placeholder="输入评论" />
            <PermissionButton permission="task:comment" type="primary" @click="addComment">发送</PermissionButton>
          </div>
          <div class="task-activity-list">
            <div v-for="comment in detail.comments" :key="comment.id" class="task-activity-row">
              <el-avatar :size="28">{{ (comment.nickname || comment.username || 'U').slice(0, 1) }}</el-avatar>
              <div>
                <strong>{{ comment.nickname || comment.username }}</strong>
                <p>{{ comment.content }}</p>
                <span>{{ formatDate(comment.createdAt) }}</span>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="工时">
          <div class="inline-form">
            <el-date-picker v-model="worklogForm.workDate" value-format="YYYY-MM-DD" placeholder="工作日期" />
            <el-input-number v-model="worklogForm.hours" :min="0.5" :step="0.5" />
            <PermissionButton permission="task:worklog" type="primary" @click="addWorklog">登记</PermissionButton>
          </div>
          <el-input v-model="worklogForm.description" class="task-inline-note" placeholder="工时说明" />
          <div class="task-activity-list">
            <div v-for="worklog in detail.worklogs" :key="worklog.id" class="task-activity-row">
              <el-avatar :size="28">{{ (worklog.nickname || worklog.username || 'U').slice(0, 1) }}</el-avatar>
              <div>
                <strong>{{ worklog.nickname || worklog.username }} · {{ worklog.hours }}h</strong>
                <p>{{ worklog.description || '无说明' }}</p>
                <span>{{ worklog.workDate }}</span>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="附件">
          <el-upload
            action="#"
            drag
            multiple
            :show-file-list="false"
            :http-request="uploadAttachment"
            class="task-attachment-upload"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          </el-upload>
          <div class="tag-editor-list task-attachment-list">
            <div v-for="attachment in detail.attachments" :key="attachment.id" class="tag-editor-row task-attachment-row">
              <div class="task-attachment-info">
                <el-tag effect="plain">{{ attachment.fileExt || 'FILE' }}</el-tag>
                <div>
                  <strong>{{ attachment.fileName || `附件 ${attachment.fileId}` }}</strong>
                  <span>{{ formatSize(attachment.fileSize) }} · {{ attachment.uploaderName || '-' }} · {{ formatDate(attachment.createdAt) }}</span>
                </div>
              </div>
              <div class="task-attachment-actions">
                <el-button link type="primary" :icon="Download" @click="downloadAttachment(attachment)">下载</el-button>
                <PermissionButton permission="task:attachment" link type="danger" @click="removeAttachment(attachment)">删除</PermissionButton>
              </div>
            </div>
            <el-empty v-if="!detail.attachments.length" description="暂无附件" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="标签">
          <div class="inline-form">
            <el-input v-model="tagForm.tagName" placeholder="标签名称" />
            <el-color-picker v-model="tagForm.tagColor" />
            <PermissionButton permission="task:tag" type="primary" @click="addTag">添加</PermissionButton>
          </div>
          <div class="project-tags project-tags--large">
            <el-tag v-for="tag in detail.tags" :key="tag.id" :style="tagStyle(tag.tagColor)">{{ tag.tagName }}</el-tag>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { userOptionsApi, type UserItem } from '@/api/system'
import { downloadFileBlobApi, uploadFileApi } from '@/api/file'
import {
  createTaskAttachmentApi,
  createTaskCommentApi,
  createTaskTagApi,
  createTaskWorklogApi,
  deleteTaskAttachmentApi,
  taskDetailApi,
  updateTaskApi,
  type TaskAttachmentItem,
  type TaskDetail
} from '@/api/task'
import { formatDateTime } from '@/utils/format'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{
  taskId: number | null
}>()
const emit = defineEmits<{
  changed: []
}>()

const detail = ref<TaskDetail | null>(null)
const users = ref<UserItem[]>([])
const assigneeId = ref<number | undefined>()
const executorIds = ref<number[]>([])
const assigning = ref(false)
const savingExecutors = ref(false)
const commentText = ref('')
const worklogForm = reactive({
  workDate: '',
  hours: 1,
  description: ''
})
const tagForm = reactive({
  tagName: '',
  tagColor: '#2563EB'
})

watch(
  () => [visible.value, props.taskId] as const,
  async ([open, taskId]) => {
    if (open && taskId) {
      await Promise.all([loadUsers(), loadDetail(taskId)])
    }
  }
)

async function loadDetail(taskId = props.taskId) {
  if (!taskId) return
  detail.value = await taskDetailApi(taskId)
  assigneeId.value = detail.value.task.assigneeId
  executorIds.value = [...(detail.value.task.executorIds || [])]
}

async function loadUsers() {
  if (users.value.length) return
  users.value = await userOptionsApi()
}

async function assignTask() {
  if (!detail.value || !assigneeId.value || assigneeId.value === detail.value.task.assigneeId) {
    return
  }
  const task = detail.value.task
  assigning.value = true
  try {
    await updateTaskApi(task.id, {
      projectId: task.projectId,
      taskNo: task.taskNo,
      title: task.title,
      description: task.description,
      assigneeId: assigneeId.value,
      priority: task.priority,
      status: task.status,
      startTime: task.startTime,
      dueTime: task.dueTime,
      estimateHours: Number(task.estimateHours || 0),
      sortNo: task.sortNo,
      tags: []
    })
    ElMessage.success('负责人已更新')
    await loadDetail()
    emit('changed')
  } finally {
    assigning.value = false
  }
}

async function saveExecutors() {
  if (!detail.value || sameIds(executorIds.value, detail.value.task.executorIds || [])) {
    return
  }
  const task = detail.value.task
  savingExecutors.value = true
  try {
    await updateTaskApi(task.id, {
      projectId: task.projectId,
      taskNo: task.taskNo,
      title: task.title,
      description: task.description,
      assigneeId: task.assigneeId,
      executorIds: executorIds.value,
      priority: task.priority,
      status: task.status,
      startTime: task.startTime,
      dueTime: task.dueTime,
      estimateHours: Number(task.estimateHours || 0),
      sortNo: task.sortNo,
      tags: []
    })
    ElMessage.success('执行人已更新')
    await loadDetail()
    emit('changed')
  } finally {
    savingExecutors.value = false
  }
}

async function addComment() {
  if (!props.taskId || !commentText.value.trim()) return
  await createTaskCommentApi({ taskId: props.taskId, content: commentText.value.trim() })
  commentText.value = ''
  ElMessage.success('评论已发送')
  await loadDetail()
  emit('changed')
}

async function addWorklog() {
  if (!props.taskId) return
  await createTaskWorklogApi({
    taskId: props.taskId,
    workDate: worklogForm.workDate,
    hours: worklogForm.hours,
    description: worklogForm.description
  })
  Object.assign(worklogForm, { workDate: '', hours: 1, description: '' })
  ElMessage.success('工时已登记')
  await loadDetail()
  emit('changed')
}

async function addTag() {
  if (!props.taskId || !tagForm.tagName.trim()) return
  await createTaskTagApi({ taskId: props.taskId, tagName: tagForm.tagName.trim(), tagColor: tagForm.tagColor })
  tagForm.tagName = ''
  ElMessage.success('标签已添加')
  await loadDetail()
  emit('changed')
}

async function uploadAttachment(options: UploadRequestOptions) {
  if (!props.taskId) {
    ElMessage.warning('请先打开任务详情')
    return
  }
  const file = options.file as File
  try {
    const uploaded = await uploadFileApi(file, { bizType: 'TASK', bizId: props.taskId })
    const attachment = await createTaskAttachmentApi({ taskId: props.taskId, fileId: uploaded.id })
    options.onSuccess?.(attachment)
    ElMessage.success(`${file.name} 已上传并关联`)
    await loadDetail()
    emit('changed')
  } catch (error) {
    throw error
  }
}

async function downloadAttachment(attachment: TaskAttachmentItem) {
  const response = await downloadFileBlobApi(attachment.fileId)
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = attachment.fileName || `task-file-${attachment.fileId}`
  link.click()
  URL.revokeObjectURL(url)
}

async function removeAttachment(attachment: TaskAttachmentItem) {
  await ElMessageBox.confirm(`确认移除附件「${attachment.fileName || attachment.fileId}」？`, '移除附件', { type: 'warning' })
  await deleteTaskAttachmentApi(attachment.id)
  ElMessage.success('附件已移除')
  await loadDetail()
  emit('changed')
}

function statusLabel(status: string) {
  const map: Record<string, string> = { TODO: '待处理', DOING: '进行中', TESTING: '测试中', DONE: '已完成', CLOSED: '已关闭' }
  return map[status] || status
}

function priorityLabel(priority: string) {
  const map: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }
  return map[priority] || priority
}

function formatDate(value?: string) {
  return formatDateTime(value)
}

function formatSize(value?: number) {
  const sizeValue = Number(value || 0)
  if (sizeValue < 1024) return `${sizeValue} B`
  if (sizeValue < 1024 * 1024) return `${(sizeValue / 1024).toFixed(1)} KB`
  if (sizeValue < 1024 * 1024 * 1024) return `${(sizeValue / 1024 / 1024).toFixed(1)} MB`
  return `${(sizeValue / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function tagStyle(color?: string) {
  const value = color || '#2563EB'
  return { color: value, borderColor: `${value}33`, background: `${value}12` }
}

function displayUser(user: UserItem) {
  return `${user.nickname || user.username} (${user.username})`
}

function sameIds(left: number[], right: number[]) {
  const leftSorted = [...left].sort((a, b) => a - b)
  const rightSorted = [...right].sort((a, b) => a - b)
  return leftSorted.length === rightSorted.length && leftSorted.every((id, index) => id === rightSorted[index])
}
</script>

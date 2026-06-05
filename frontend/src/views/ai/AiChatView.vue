<template>
  <PageContainer class="ai-page" title="AI助手" description="支持普通问答、知识库问答、文档总结、代码生成与 SQL 助手">
    <div class="ai-layout">
      <aside class="ai-session-panel">
        <div class="ai-session-toolbar">
          <strong>会话列表</strong>
          <PermissionButton permission="ai:session" type="primary" :icon="Plus" @click="startNewChat">新建会话</PermissionButton>
        </div>
        <el-input
          v-model="sessionKeyword"
          class="ai-search"
          placeholder="搜索会话"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadSessions"
        />
        <div class="ai-session-list">
          <button
            v-for="session in sessions"
            :key="session.id"
            class="ai-session-item"
            :class="{ 'is-active': activeSessionId === session.id }"
            @click="selectSession(session)"
          >
            <span class="ai-session-item__icon">
              <el-icon><ChatDotRound /></el-icon>
            </span>
            <span class="ai-session-item__main">
              <strong>{{ session.sessionTitle }}</strong>
              <small>{{ session.sessionType }} · {{ session.messageCount }} 条消息</small>
            </span>
            <el-tag v-if="session.modelName === 'mock-ai'" size="small" type="warning">Mock</el-tag>
            <el-button
              link
              type="danger"
              size="small"
              class="ai-session-delete"
              :icon="Delete"
              title="删除会话"
              @click.stop="removeSession(session)"
            />
          </button>
          <el-empty v-if="!sessionLoading && sessions.length === 0" description="暂无会话" />
        </div>
      </aside>

      <section class="ai-chat-panel">
        <div class="ai-chat-header">
          <div>
            <h2>{{ activeSession?.sessionTitle || '新会话' }}</h2>
            <p>{{ displayModelName }} · {{ modeLabel(activeMode) }} · {{ useKnowledge ? '知识库增强' : '大模型直答' }}</p>
          </div>
          <div class="ai-chat-status">
            <span :class="{ 'is-live': !isMockDisplay && !isRuntimeUnknown, 'is-pending': isRuntimeUnknown }"></span>
            {{ runtimeModeLabel }}
          </div>
        </div>

        <div ref="messageListRef" class="ai-message-list">
          <div v-if="messages.length === 0" class="ai-empty-state">
            <div class="ai-empty-state__icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <h3>开始一次 AI 协作</h3>
            <p>可以让助手总结面试讲解点、从知识库查找引用，或生成项目任务拆解。</p>
          </div>

          <article
            v-for="message in messages"
            :key="message.id"
            class="ai-message"
            :class="message.role === 'USER' ? 'is-user' : 'is-assistant'"
          >
            <div class="ai-message__avatar">
              <el-icon v-if="message.role === 'ASSISTANT'"><Cpu /></el-icon>
              <span v-else>我</span>
            </div>
            <div class="ai-message__body">
              <div class="ai-message__meta">
                <strong>{{ message.role === 'ASSISTANT' ? 'TeamFlow AI' : '我' }}</strong>
                <span>{{ formatDate(message.createdAt) }}</span>
                <el-tag v-if="message.role === 'ASSISTANT'" size="small">{{ message.tokens }} tokens</el-tag>
              </div>
              <MdPreview v-if="message.role === 'ASSISTANT'" :model-value="message.content" />
              <p v-else class="ai-message__plain">{{ message.content }}</p>
              <div v-if="message.references?.length" class="ai-inline-references">
                <span v-for="(reference, index) in message.references" :key="referenceKey(reference, index)">
                  [{{ index + 1 }}] {{ reference.title }}
                </span>
              </div>
            </div>
          </article>

          <article v-if="sending && !isStreaming" class="ai-message is-assistant ai-message--thinking">
            <div class="ai-message__avatar">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="ai-message__body">
              <div class="ai-message__meta">
                <strong>TeamFlow AI</strong>
                <span>正在生成回复</span>
              </div>
              <div class="ai-thinking-dots">
                <span />
                <span />
                <span />
              </div>
            </div>
          </article>

          <article v-if="isStreaming" class="ai-message is-assistant">
            <div class="ai-message__avatar">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="ai-message__body">
              <div class="ai-message__meta">
                <strong>TeamFlow AI</strong>
                <span>正在输出</span>
              </div>
              <MdPreview :model-value="streamingContent" />
              <span class="ai-stream-cursor" />
            </div>
          </article>
        </div>

        <div class="ai-composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="4"
            resize="none"
            placeholder="输入问题，例如：请总结 TeamFlow AI 的面试讲解亮点"
            @keydown.ctrl.enter.prevent="sendMessage"
            @keydown.meta.enter.prevent="sendMessage"
          />
          <div class="ai-composer__footer">
            <div class="ai-composer__hint">
              <el-icon><Connection /></el-icon>
              Ctrl / Command + Enter 发送
            </div>
            <PermissionButton permission="ai:chat" type="primary" :icon="Promotion" :loading="sending" @click="sendMessage">
              发送
            </PermissionButton>
          </div>
        </div>
      </section>

      <aside class="ai-side-panel">
        <el-card shadow="never" class="ai-tool-card">
          <template #header>
            <div class="ai-card-title">
              <el-icon><MagicStick /></el-icon>
              <span>助手模式</span>
            </div>
          </template>
          <el-segmented v-model="activeMode" :options="modeOptions" class="ai-mode-switch" />
          <p class="ai-muted">{{ activeModeDescription }}</p>

          <div class="ai-rag-toggle">
            <div class="ai-rag-toggle__text">
              <strong>知识库增强</strong>
              <small>{{ useKnowledge ? '检索已发布文档并标注引用来源' : '直连大模型直答，不检索知识库' }}</small>
            </div>
            <el-switch v-model="useKnowledge" />
          </div>

          <div class="ai-model-field">
            <span class="ai-model-field__label">模型</span>
            <el-select v-model="selectedModel" class="ai-model-select">
              <el-option v-for="model in runtimeModelOptions" :key="model.value" :label="model.label" :value="model.value" />
            </el-select>
          </div>
        </el-card>

        <el-card shadow="never" class="ai-tool-card" :class="{ 'is-disabled': !useKnowledge }">
          <template #header>
            <div class="ai-card-title">
              <el-icon><Reading /></el-icon>
              <span>知识空间</span>
            </div>
          </template>
          <el-select
            v-model="selectedSpaceId"
            clearable
            :disabled="!useKnowledge"
            :placeholder="useKnowledge ? '全部知识空间' : '开启知识库增强后可选'"
            @change="loadSessions"
          >
            <el-option v-for="space in spaces" :key="space.id" :label="space.spaceName" :value="space.id" />
          </el-select>
          <div class="ai-rag-status">
            <strong>{{ useKnowledge ? selectedSpaceName : '未启用知识库' }}</strong>
            <span>仅召回已发布文档，发布或回滚版本后自动刷新检索切片。</span>
          </div>
        </el-card>

        <el-card shadow="never" class="ai-tool-card ai-reference-card">
          <template #header>
            <div class="ai-card-title">
              <el-icon><Document /></el-icon>
              <span>引用来源</span>
            </div>
          </template>
          <div v-if="latestReferences.length" class="ai-reference-list">
            <div v-for="(reference, index) in latestReferences" :key="referenceKey(reference, index)" class="ai-reference-item">
              <div class="ai-reference-item__head">
                <span>[{{ index + 1 }}]</span>
                <strong>{{ reference.title }}</strong>
              </div>
              <div class="ai-reference-item__meta">
                <el-tag v-if="reference.spaceName" size="small" effect="plain">{{ reference.spaceName }}</el-tag>
                <el-tag v-if="reference.versionNo" size="small" type="success" effect="plain">v{{ reference.versionNo }}</el-tag>
                <el-tag v-if="reference.chunkIndex !== undefined" size="small" type="info" effect="plain">片段 {{ reference.chunkIndex + 1 }}</el-tag>
                <el-tag v-if="reference.score !== undefined" size="small" type="warning" effect="plain">相关度 {{ reference.score }}</el-tag>
              </div>
              <p>{{ reference.snippet }}</p>
            </div>
          </div>
          <el-empty v-else description="开启知识库增强后会显示引用来源" />
        </el-card>
      </aside>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Connection, Cpu, Delete, Document, MagicStick, Plus, Promotion, Reading, Search } from '@element-plus/icons-vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import PageContainer from '@/components/PageContainer.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { knowledgeSpacePageApi, type KnowledgeSpaceItem } from '@/api/knowledge'
import { loadProfilePreferences } from '@/utils/profilePreferences'
import {
  aiChatStreamApi,
  aiMessagePageApi,
  aiProviderStatusApi,
  aiSessionPageApi,
  deleteAiSessionApi,
  type AiMessageItem,
  type AiReferenceItem,
  type AiSessionItem
} from '@/api/ai'

const modeOptions = [
  { label: '问答', value: 'CHAT' },
  { label: '总结', value: 'SUMMARY' },
  { label: '代码', value: 'CODE' },
  { label: 'SQL', value: 'SQL' }
]

const modelOptions = [
  { label: 'DeepSeek Chat（快速）', value: 'deepseek-chat' },
  { label: 'DeepSeek R1（深度推理）', value: 'deepseek-reasoner' }
]

const DEFAULT_MODEL = 'deepseek-chat'

const sessionKeyword = ref('')
const sessionLoading = ref(false)
const sending = ref(false)
const sessions = ref<AiSessionItem[]>([])
const messages = ref<AiMessageItem[]>([])
const spaces = ref<KnowledgeSpaceItem[]>([])
const activeSessionId = ref<number | null>(null)
const activeMode = ref(loadPreferredAiMode())
const useKnowledge = ref(false)
const selectedModel = ref(DEFAULT_MODEL)
const selectedSpaceId = ref<number | undefined>()
const input = ref('')
const runtimeModel = ref(DEFAULT_MODEL)
const runtimeMock = ref<boolean | null>(null)
const latestMock = ref<boolean | null>(null)
const latestReferences = ref<AiReferenceItem[]>([])
const messageListRef = ref<HTMLElement | null>(null)
const streamingContent = ref('')
const isStreaming = ref(false)
let temporaryMessageId = -1

const activeSession = computed(() => sessions.value.find((session) => session.id === activeSessionId.value))
const isRuntimeUnknown = computed(() => runtimeMock.value === null && latestMock.value === null)
const isMockDisplay = computed(() => latestMock.value ?? runtimeMock.value ?? false)
const runtimeModelOptions = computed(() => runtimeMock.value
  ? [{ label: 'Mock AI（演示）', value: 'mock-ai' }]
  : modelOptions)
const runtimeModeLabel = computed(() => {
  if (isRuntimeUnknown.value) {
    return '模型状态检测中'
  }
  return isMockDisplay.value ? 'Mock 演示模式' : '真实模型模式'
})
const displayModelName = computed(() => {
  const sessionModel = activeSession.value?.modelName
  if (isMockDisplay.value) {
    return 'mock-ai'
  }
  if (sessionModel === 'mock-ai' && runtimeMock.value === false) {
    return runtimeModel.value
  }
  return sessionModel || selectedModel.value
})
const selectedSpaceName = computed(() => {
  if (!selectedSpaceId.value) {
    return '全部知识空间'
  }
  return spaces.value.find((space) => space.id === selectedSpaceId.value)?.spaceName || '当前知识空间'
})
const activeModeDescription = computed(() => {
  const map: Record<string, string> = {
    CHAT: '普通问答适合快速咨询、方案讨论和日常协作问题。',
    KNOWLEDGE: '知识库问答会检索已发布文档，并在回复中展示引用来源。',
    SUMMARY: '文档总结适合提炼会议纪要、需求说明和长文档重点。',
    CODE: '代码生成适合输出示例代码、接口草案和重构建议。',
    SQL: 'SQL 助手适合生成查询语句、解释表结构和排查数据问题。'
  }
  return map[activeMode.value] || '选择助手模式后，系统会按场景调整提示词与回答结构。'
})

onMounted(async () => {
  await Promise.all([loadProviderStatus(), loadSpaces(), loadSessions()])
  if (sessions.value.length) {
    await selectSession(sessions.value[0])
  }
})

async function loadProviderStatus() {
  const status = await aiProviderStatusApi()
  runtimeMock.value = status.mock
  runtimeModel.value = status.mock ? status.model : normalizeModel(status.model)
  selectedModel.value = status.mock ? 'mock-ai' : runtimeModel.value
}

async function loadSpaces() {
  const result = await knowledgeSpacePageApi({ page: 1, size: 50 })
  spaces.value = result.records
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const result = await aiSessionPageApi({ page: 1, size: 30, keyword: sessionKeyword.value, mine: true })
    sessions.value = result.records
  } finally {
    sessionLoading.value = false
  }
}

async function selectSession(session: AiSessionItem) {
  activeSessionId.value = session.id
  const sessionType = session.sessionType || 'CHAT'
  // 历史 KNOWLEDGE 会话归一化为「问答 + 知识库增强开」，与新的解耦模型保持一致
  useKnowledge.value = sessionType === 'KNOWLEDGE' || Boolean(session.spaceId)
  activeMode.value = sessionType === 'KNOWLEDGE' ? 'CHAT' : sessionType
  selectedSpaceId.value = session.spaceId
  selectedModel.value = runtimeMock.value ? 'mock-ai' : normalizeModel(session.modelName)
  latestMock.value = null
  await loadMessages(session.id)
}

function normalizeModel(modelName?: string) {
  return modelOptions.some((item) => item.value === modelName) ? (modelName as string) : DEFAULT_MODEL
}

async function loadMessages(sessionId: number) {
  const result = await aiMessagePageApi({ page: 1, size: 100, sessionId })
  messages.value = result.records
  latestReferences.value = [...messages.value].reverse().find((message) => message.references?.length)?.references || []
  scrollToBottom()
}

function startNewChat() {
  activeSessionId.value = null
  messages.value = []
  latestReferences.value = []
  activeMode.value = loadPreferredAiMode()
  useKnowledge.value = false
  selectedSpaceId.value = undefined
  selectedModel.value = runtimeMock.value ? 'mock-ai' : runtimeModel.value
  input.value = ''
  latestMock.value = null
}

async function sendMessage() {
  const content = input.value.trim()
  if (!content) {
    ElMessage.warning('请输入消息内容')
    return
  }
  if (sending.value) {
    return
  }
  sending.value = true
  isStreaming.value = false
  streamingContent.value = ''
  input.value = ''

  const temporaryUserMessage = createTemporaryUserMessage(content)
  messages.value = [...messages.value, temporaryUserMessage]
  scrollToBottom()

  try {
    await aiChatStreamApi(
      {
        sessionId: activeSessionId.value || undefined,
        spaceId: useKnowledge.value ? selectedSpaceId.value : undefined,
        mode: activeMode.value,
        useKnowledge: useKnowledge.value,
        model: selectedModel.value,
        message: content,
      },
      (token) => {
        if (!isStreaming.value) isStreaming.value = true
        streamingContent.value += token
        scrollToBottom()
      },
      (response) => {
        isStreaming.value = false
        streamingContent.value = ''
        activeSessionId.value = response.session.id
        latestMock.value = response.mock
        latestReferences.value = response.references
        messages.value = [
          ...messages.value.filter((m) => m.id !== temporaryUserMessage.id),
          response.userMessage,
          response.assistantMessage,
        ]
        loadSessions()
        scrollToBottom()
      },
      (errorMsg) => {
        isStreaming.value = false
        streamingContent.value = ''
        messages.value = messages.value.filter((m) => m.id !== temporaryUserMessage.id)
        input.value = content
        ElMessage.error(errorMsg)
        scrollToBottom()
      }
    )
  } catch (error) {
    isStreaming.value = false
    streamingContent.value = ''
    messages.value = messages.value.filter((m) => m.id !== temporaryUserMessage.id)
    input.value = content
    scrollToBottom()
  } finally {
    sending.value = false
    isStreaming.value = false
  }
}

function createTemporaryUserMessage(content: string): AiMessageItem {
  return {
    id: temporaryMessageId--,
    sessionId: activeSessionId.value || 0,
    role: 'USER',
    content,
    tokens: 0,
    references: [],
    createdAt: localDateTimeNow()
  }
}

// 与后端 LocalDateTime 保持一致：输出本地时区的 "YYYY-MM-DDTHH:mm:ss"，
// 避免 toISOString() 的 UTC 时间让乐观气泡比真实消息早 8 小时。
function localDateTimeNow(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
    + `T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

async function removeSession(session: AiSessionItem) {
  await ElMessageBox.confirm(`确定删除会话「${session.sessionTitle}」吗？`, '删除会话', { type: 'warning' })
  await deleteAiSessionApi(session.id)
  if (activeSessionId.value === session.id) {
    startNewChat()
  }
  await loadSessions()
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function modeLabel(mode: string) {
  return modeOptions.find((item) => item.value === mode)?.label || mode
}

function formatDate(value?: string) {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(0, 16)
}

function referenceKey(reference: AiReferenceItem, index: number) {
  return `${reference.docId}-${reference.chunkIndex ?? index}`
}

function loadPreferredAiMode() {
  const mode = loadProfilePreferences().defaultAiMode
  return modeOptions.some((item) => item.value === mode) ? mode : 'CHAT'
}
</script>

<style scoped>
:deep(.page-container__header > div:first-child) {
  min-width: 0;
}

:deep(.page-container__header p) {
  max-width: 760px;
  overflow-wrap: anywhere;
}

.ai-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.ai-page :deep(.page-container__header) {
  flex: 0 0 auto;
}

.ai-page :deep(.page-container__body) {
  flex: 1;
  min-height: 0;
  display: flex;
}

.ai-layout {
  width: 100%;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) clamp(340px, 26vw, 390px);
  gap: 18px;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ai-session-panel,
.ai-chat-panel,
.ai-side-panel {
  height: 100%;
  min-height: 0;
}

.ai-session-panel {
  display: flex;
  flex-direction: column;
  padding: 16px;
  border: 1px solid var(--tf-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--tf-shadow);
}

.ai-session-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.ai-session-toolbar strong {
  color: var(--tf-text);
  font-size: 15px;
  font-weight: 800;
}

.ai-session-toolbar :deep(.el-button) {
  flex: 0 0 auto;
  height: 34px;
  border-radius: 8px;
  font-weight: 700;
}

.ai-search {
  margin-bottom: 14px;
}

.ai-session-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  padding: 2px 2px 8px;
  scroll-padding-top: 2px;
}

.ai-session-item {
  width: 100%;
  min-height: 78px;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--tf-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.ai-session-item:hover {
  border-color: rgba(37, 99, 235, 0.26);
  background: #eff6ff;
  transform: translateY(-1px);
}

.ai-session-item.is-active {
  border-color: rgba(37, 99, 235, 0.32);
  background: #eff6ff;
  transform: none;
}

.ai-session-item__icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.13), rgba(124, 58, 237, 0.12));
  color: var(--tf-primary);
}

.ai-session-item__main {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.ai-session-item__main strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-session-item__main strong {
  display: -webkit-box;
  line-height: 1.35;
  font-size: 14px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  white-space: normal;
}

.ai-session-item__main small {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-session-item__main small {
  color: var(--tf-muted);
  font-size: 12px;
}

.ai-session-item :deep(.el-tag) {
  flex: 0 0 auto;
}

.ai-session-delete {
  flex: 0 0 auto;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.ai-session-item:hover .ai-session-delete,
.ai-session-item.is-active .ai-session-delete {
  opacity: 1;
}

.ai-chat-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--tf-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--tf-shadow);
}

.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--tf-border);
  background: linear-gradient(180deg, #fff, #f8fbff);
}

.ai-chat-header > div:first-child {
  min-width: 0;
}

.ai-chat-header h2 {
  margin: 0;
  overflow: hidden;
  color: var(--tf-text);
  font-size: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat-header p {
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--tf-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  padding: 7px 10px;
  border-radius: 999px;
  background: #f9fafb;
  color: var(--tf-muted);
  font-size: 12px;
  font-weight: 700;
}

.ai-chat-status span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--tf-warning);
}

.ai-chat-status span.is-live {
  background: var(--tf-success);
}

.ai-chat-status span.is-pending {
  background: var(--tf-muted);
}

.ai-message-list {
  overflow: auto;
  padding: 22px;
  min-height: 0;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.06), transparent 28%),
    #fbfcff;
  scrollbar-gutter: stable;
}

.ai-empty-state {
  height: 100%;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--tf-muted);
  text-align: center;
}

.ai-empty-state__icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  margin-bottom: 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--tf-primary), var(--tf-secondary));
  color: #fff;
  font-size: 24px;
}

.ai-empty-state h3 {
  margin: 0 0 6px;
  color: var(--tf-text);
  font-size: 20px;
}

.ai-empty-state p {
  max-width: 360px;
  margin: 0;
}

.ai-message {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.ai-message.is-user {
  flex-direction: row-reverse;
}

.ai-message__avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--tf-primary), var(--tf-secondary));
  color: #fff;
  font-weight: 800;
}

.ai-message.is-user .ai-message__avatar {
  background: #111827;
}

.ai-message__body {
  max-width: min(760px, 86%);
  padding: 14px 16px;
  border: 1px solid rgba(229, 231, 235, 0.9);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(17, 24, 39, 0.05);
}

.ai-message.is-user .ai-message__body {
  border-color: transparent;
  background: linear-gradient(135deg, var(--tf-primary), var(--tf-secondary));
  color: #fff;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.18);
}

.ai-message__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--tf-muted);
  font-size: 12px;
}

.ai-message__meta strong {
  color: var(--tf-text);
  font-size: 13px;
}

.ai-message.is-user .ai-message__meta,
.ai-message.is-user .ai-message__meta strong {
  color: rgba(255, 255, 255, 0.82);
}

.ai-message__plain {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.75;
}

.ai-message--thinking .ai-message__body {
  width: auto;
  min-width: 236px;
  max-width: 300px;
  padding: 16px 18px;
}

.ai-message--thinking .ai-message__meta {
  display: grid;
  grid-template-columns: auto 1fr;
  column-gap: 12px;
  align-items: baseline;
  margin-bottom: 12px;
  white-space: nowrap;
}

.ai-message--thinking .ai-message__meta strong {
  font-size: 14px;
}

.ai-message--thinking .ai-message__meta span {
  overflow: hidden;
  color: var(--tf-muted);
  font-size: 13px;
  text-overflow: ellipsis;
}

.ai-thinking-dots {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 18px;
}

.ai-thinking-dots span {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--tf-primary);
  opacity: 0.35;
  animation: ai-thinking 1s infinite ease-in-out;
}

.ai-thinking-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-thinking-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes ai-thinking {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }

  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.ai-message__body :deep(.md-editor-preview-wrapper) {
  padding: 0;
  background: transparent;
}

.ai-message__body :deep(.md-editor-preview) {
  color: var(--tf-text);
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.ai-message.is-user .ai-message__body :deep(.md-editor-preview) {
  color: #fff;
}

.ai-message__body :deep(.md-editor-preview p) {
  margin: 0 0 8px;
}

.ai-message__body :deep(.md-editor-preview p:last-child) {
  margin-bottom: 0;
}

.ai-message__body :deep(.md-editor-preview pre) {
  border-radius: 12px;
  background: #111827;
}

.ai-message__body :deep(.md-editor-preview code) {
  white-space: pre-wrap;
}

.ai-inline-references {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.ai-inline-references span {
  padding: 4px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: var(--tf-primary);
  font-size: 12px;
  font-weight: 700;
}

.ai-composer {
  padding: 16px;
  border-top: 1px solid var(--tf-border);
  background: #fff;
}

.ai-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
}

.ai-composer__hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--tf-muted);
  font-size: 12px;
}

.ai-side-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden auto;
  scrollbar-gutter: stable;
}

.ai-tool-card {
  min-width: 0;
  border: 1px solid var(--tf-border);
  border-radius: 16px;
  box-shadow: var(--tf-shadow);
}

.ai-tool-card :deep(.el-card__header),
.ai-tool-card :deep(.el-card__body) {
  min-width: 0;
}

.ai-side-panel .ai-tool-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.ai-side-panel .ai-tool-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.ai-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
}

.ai-card-title .el-icon {
  color: var(--tf-primary);
}

.ai-mode-switch {
  width: 100%;
}

.ai-muted {
  margin: 8px 0 0;
  color: var(--tf-muted);
  font-size: 13px;
  line-height: 1.5;
}

.ai-rag-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--tf-border);
  border-radius: 12px;
  background: #f9fafb;
}

.ai-rag-toggle__text {
  display: grid;
  gap: 2px;
}

.ai-rag-toggle__text strong {
  font-size: 13px;
  color: var(--tf-text);
}

.ai-rag-toggle__text small {
  color: var(--tf-muted);
  font-size: 12px;
}

.ai-model-field {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.ai-model-field__label {
  flex: 0 0 auto;
  color: var(--tf-muted);
  font-size: 13px;
  font-weight: 700;
}

.ai-model-select {
  flex: 1;
}

.ai-tool-card.is-disabled {
  opacity: 0.6;
}

.ai-rag-status {
  display: grid;
  gap: 4px;
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 12px;
  background: #eff6ff;
}

.ai-rag-status strong {
  color: var(--tf-primary);
  font-size: 13px;
}

.ai-rag-status span {
  color: var(--tf-muted);
  font-size: 12px;
  line-height: 1.55;
}

.ai-reference-card {
  flex: 1;
  min-height: 240px;
  display: flex;
  flex-direction: column;
}

.ai-reference-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.ai-reference-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-reference-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(229, 231, 235, 0.9);
  border-radius: 12px;
  background: #f9fafb;
  overflow: hidden;
}

.ai-reference-item strong {
  color: var(--tf-text);
}

.ai-reference-item__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-reference-item__head > span {
  width: 26px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.12), rgba(124, 58, 237, 0.1));
  color: var(--tf-primary);
  font-size: 12px;
  font-weight: 800;
}

.ai-reference-item__head strong {
  min-width: 0;
  font-size: 14px;
  line-height: 1.45;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ai-reference-item__meta {
  display: flex;
  flex-wrap: wrap;
  min-width: 0;
  gap: 6px;
  margin-bottom: 8px;
}

.ai-reference-item__meta :deep(.el-tag) {
  max-width: 100%;
  height: auto;
  min-height: 24px;
  white-space: normal;
}

.ai-reference-item p {
  margin: 0;
  color: var(--tf-muted);
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ai-stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--tf-primary);
  animation: ai-cursor-blink 0.7s step-end infinite;
}

@keyframes ai-cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@media (max-width: 1180px) {
  .ai-layout {
    grid-template-columns: 260px minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) auto;
    height: 100%;
    min-height: 0;
    overflow: hidden;
  }

  .ai-side-panel {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    height: auto;
    max-height: 320px;
    overflow: auto;
  }

  .ai-reference-card {
    grid-column: 1 / -1;
    min-height: 180px;
  }

  .ai-chat-panel {
    height: auto;
    min-height: 0;
  }
}

@media (max-width: 900px) {
  .ai-page {
    height: auto;
    min-height: 100%;
  }

  .ai-page :deep(.page-container__body) {
    display: block;
  }

  .ai-layout {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .ai-side-panel {
    grid-template-columns: 1fr;
    max-height: none;
    overflow: visible;
  }

  .ai-session-panel,
  .ai-chat-panel {
    height: auto;
    min-height: 0;
  }

  .ai-message-list {
    max-height: 56vh;
  }

  .ai-reference-card {
    flex: initial;
    min-height: 260px;
  }

  .ai-reference-card :deep(.el-card__body) {
    overflow: visible;
  }
}
</style>

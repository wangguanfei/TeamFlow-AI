import http from './request'
import type { PageResult } from './system'

// 后端 RAG 返回的引用快照。retrievalSource 用于区分向量召回、关键词召回和混合命中，
// 前端展示只依赖这些字段，不再回查知识库文档，保证历史消息可复现当时的引用来源。
export interface AiReferenceItem {
  docId: number
  title: string
  snippet: string
  spaceId?: number
  spaceName?: string
  versionNo?: number
  chunkIndex?: number
  score?: number
  chunkId?: string
  denseScore?: number
  keywordScore?: number
  retrievalSource?: 'VECTOR' | 'KEYWORD' | 'HYBRID'
}

export interface AiSessionItem {
  id: number
  userId: number
  username?: string
  spaceId?: number
  spaceName?: string
  sessionTitle: string
  modelName: string
  sessionType: string
  messageCount: number
  createdAt?: string
  updatedAt?: string
}

export interface AiMessageItem {
  id: number
  sessionId: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  tokens: number
  references: AiReferenceItem[]
  createdAt?: string
}

export interface AiMessageFeedbackRequest {
  rating: number
  reason?: 'HELPFUL' | 'NOT_HELPFUL' | 'BAD_REFERENCE' | 'OUTDATED'
  expectedDocId?: number
  comment?: string
}

export interface AiMessageFeedbackItem {
  id: number
  messageId: number
  userId: number
  rating: number
  reason?: string
  expectedDocId?: number
  comment?: string
  createdAt?: string
  updatedAt?: string
}

export interface AiChatResponse {
  session: AiSessionItem
  userMessage: AiMessageItem
  assistantMessage: AiMessageItem
  references: AiReferenceItem[]
  mock: boolean
}

export interface AgentActionResult {
  actionId?: number
  status: string
  summary: string
  targetType?: string
  targetId?: number
  url?: string
}

export interface AgentPendingAction {
  actionId: number
  // confirmToken 只用于本次确认，后端不会把明文 token 落库。
  confirmToken: string
  toolName: string
  toolLabel: string
  preview: Record<string, unknown>
}

export interface AgentActionLogItem {
  id: number
  sessionId?: number
  messageId?: number
  userId: number
  username?: string
  confirmedBy?: number
  confirmedByUsername?: string
  toolName: string
  toolLabel?: string
  argumentsJson?: string
  previewJson?: string
  resultJson?: string
  isWrite: number
  status: string
  confirmedAt?: string
  targetType?: string
  targetId?: number
  errorMessage?: string
  durationMs?: number
  createdAt: string
  updatedAt?: string
}

export interface AgentActionLogQueryParams {
  page?: number
  size?: number
  username?: string
  toolName?: string
  status?: string
  isWrite?: number
  startTime?: string
  endTime?: string
}

export interface AiProviderStatus {
  provider: string
  model: string
  configured: boolean
  mock: boolean
}

export interface RagStatus {
  enabled: boolean
  workerEnabled: boolean
  qdrantAvailable: boolean
  embeddingAvailable: boolean
  memoryGatePassed: boolean
  memAvailableMb: number
  pendingJobs: number
  runningJobs: number
  failedJobs: number
  qdrantCollection: string
  embeddingModel: string
}

export interface RagRebuildResponse {
  queuedJobs: number
}

export function aiProviderStatusApi() {
  return http.get<unknown, AiProviderStatus>('/ai/status')
}

export function ragStatusApi() {
  return http.get<unknown, RagStatus>('/rag/status')
}

export function rebuildRagDocumentApi(docId: number) {
  return http.post<unknown, RagRebuildResponse>(`/rag/index/documents/${docId}/rebuild`)
}

export function rebuildRagIndexApi(params?: { spaceId?: number }) {
  return http.post<unknown, RagRebuildResponse>('/rag/index/rebuild', undefined, { params })
}

export function aiSessionPageApi(params: { page?: number; size?: number; keyword?: string; mine?: boolean }) {
  return http.get<unknown, PageResult<AiSessionItem>>('/ai-sessions/page', { params })
}

export function createAiSessionApi(data: { sessionTitle: string; sessionType?: string; spaceId?: number }) {
  return http.post<unknown, AiSessionItem>('/ai-sessions', data)
}

export function deleteAiSessionApi(id: number) {
  return http.delete<unknown, null>(`/ai-sessions/${id}`)
}

export function aiMessagePageApi(params: { page?: number; size?: number; sessionId?: number; keyword?: string }) {
  return http.get<unknown, PageResult<AiMessageItem>>('/ai-messages/page', { params })
}

export interface AiChatPayload {
  sessionId?: number
  spaceId?: number
  mode?: string
  useKnowledge?: boolean
  model?: string
  message: string
}

export function aiKnowledgeAskApi(data: AiChatPayload) {
  return http.post<unknown, AiChatResponse>('/ai/knowledge/ask', {
    ...data,
    mode: 'KNOWLEDGE',
    useKnowledge: true,
  })
}

export function aiMessageFeedbackApi(messageId: number, data: AiMessageFeedbackRequest) {
  return http.post<unknown, AiMessageFeedbackItem>(`/ai-messages/${messageId}/feedback`, data)
}

/**
 * 普通 AI 聊天流式接口。
 *
 * 后端通过 Spring SseEmitter 推送 data: JSON 行，事件类型固定为：
 * token：增量文本；done：完整响应和消息 ID；error：可展示错误。
 */
export async function aiChatStreamApi(
  data: AiChatPayload,
  onToken: (token: string) => void,
  onDone: (response: AiChatResponse) => void,
  onError: (message: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('teamflow_access_token')
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '/api'

  const response = await fetch(`${baseUrl}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!response.ok) {
    onError(await readErrorMessage(response))
    return
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false

  while (!finished) {
    const { done, value } = await reader.read()
    if (done) break

    // 浏览器读取到的 chunk 不保证按 SSE 行切开，所以保留最后一个不完整行到下一轮继续拼。
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const jsonStr = line.slice(5).trim()
      if (!jsonStr) continue
      try {
        const event = JSON.parse(jsonStr) as Record<string, unknown>
        if (event.type === 'token') {
          onToken(event.content as string)
        } else if (event.type === 'done') {
          const { type: _type, ...rest } = event
          onDone(rest as unknown as AiChatResponse)
          finished = true
          break
        } else if (event.type === 'error') {
          onError((event.message as string) || 'AI 服务异常')
          finished = true
          break
        }
      } catch {
        // 单个坏事件不应中断整个流，服务端后续 done/error 仍可能正常到达。
        // ignore malformed SSE events
      }
    }
  }
}

/**
 * Agent 聊天流式接口。
 *
 * 相比普通聊天多了 agent_tool_call、agent_tool_result、agent_pending_action。
 * pending_action 表示后端只生成了写操作预览，真正写库要再调用 confirmAgentActionApi。
 */
export async function aiAgentChatStreamApi(
  data: AiChatPayload,
  handlers: {
    onStatus?: (message: string) => void
    onToolCall?: (payload: Record<string, unknown>) => void
    onToolResult?: (payload: Record<string, unknown>) => void
    onPendingAction?: (action: AgentPendingAction) => void
    onDone: (payload: { sessionId: number; assistantMessageId?: number; mock?: boolean }) => void
    onError: (message: string) => void
  },
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('teamflow_access_token')
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '/api'

  const response = await fetch(`${baseUrl}/ai/agent/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!response.ok) {
    handlers.onError(await readErrorMessage(response))
    return
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false

  while (!finished) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const jsonStr = line.slice(5).trim()
      if (!jsonStr) continue
      try {
        const event = JSON.parse(jsonStr) as Record<string, unknown>
        if (event.type === 'agent_status') {
          handlers.onStatus?.((event.message as string) || '处理中')
        } else if (event.type === 'agent_tool_call') {
          handlers.onToolCall?.(event)
        } else if (event.type === 'agent_tool_result') {
          handlers.onToolResult?.(event)
        } else if (event.type === 'agent_pending_action') {
          handlers.onPendingAction?.(event as unknown as AgentPendingAction)
        } else if (event.type === 'agent_done') {
          handlers.onDone(event as unknown as { sessionId: number; assistantMessageId?: number; mock?: boolean })
          finished = true
          break
        } else if (event.type === 'agent_error') {
          handlers.onError((event.message as string) || 'AI 企业助理执行失败')
          finished = true
          break
        }
      } catch {
        // ignore malformed SSE events
      }
    }
  }
}

export function confirmAgentActionApi(confirmToken: string) {
  return http.post<unknown, AgentActionResult>('/ai/agent/confirm', { confirmToken })
}

export function cancelAgentActionApi(confirmToken: string) {
  return http.post<unknown, AgentActionResult>('/ai/agent/cancel', { confirmToken })
}

export function agentActionLogPageApi(params: AgentActionLogQueryParams) {
  return http.get<unknown, PageResult<AgentActionLogItem>>('/ai/agent/actions', { params })
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const payload = await response.clone().json() as { message?: string }
    return payload.message || `请求失败 (${response.status})`
  } catch {
    try {
      const text = await response.text()
      return text || `请求失败 (${response.status})`
    } catch {
      return `请求失败 (${response.status})`
    }
  }
}

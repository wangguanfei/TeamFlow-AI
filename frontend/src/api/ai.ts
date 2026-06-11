import http from './request'
import type { PageResult } from './system'

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
        // ignore malformed SSE events
      }
    }
  }
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

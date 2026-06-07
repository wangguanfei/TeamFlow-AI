import http from './request'
import type { PageResult } from './system'

export type DeployTarget = 'all' | 'backend' | 'frontend'
export type DeployStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface DeployRecordItem {
  id: number
  target: DeployTarget
  status: DeployStatus
  triggerUsername: string
  exitCode: number | null
  startedAt: string
  finishedAt: string | null
  costMs: number | null
}

export interface DeployCurrentResponse {
  running: boolean
  deployId: number | null
}

export interface DeployTriggerRequest {
  target: DeployTarget
  skipPull: boolean
}

export function triggerDeployApi(data: DeployTriggerRequest) {
  return http.post<unknown, number>('/deploy/trigger', data)
}

export function currentDeployApi(silent = false) {
  return http.get<unknown, DeployCurrentResponse>('/deploy/current',
    silent ? ({ silentError: true } as never) : undefined
  )
}

export function deployPageApi(params: { page?: number; size?: number }) {
  return http.get<unknown, PageResult<DeployRecordItem>>('/deploy/page', { params })
}

export async function deployLogStreamApi(
  deployId: number,
  onLog: (line: string) => void,
  onDone: (status: DeployStatus, exitCode: number, costMs: number) => void,
  onError: (msg: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('teamflow_access_token')
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '/api'

  const response = await fetch(`${baseUrl}/deploy/stream/${deployId}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal,
  })

  if (!response.ok) {
    onError(`连接失败 (${response.status})`)
    return
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const raw = line.slice(5).trim()
      if (!raw) continue
      try {
        const event = JSON.parse(raw) as {
          type: string
          content?: string
          status?: DeployStatus
          exitCode?: number
          costMs?: number
        }
        if (event.type === 'log' && event.content !== undefined) {
          onLog(event.content)
        } else if (event.type === 'done') {
          onDone(event.status ?? 'FAILED', event.exitCode ?? -1, event.costMs ?? 0)
        } else if (event.type === 'error') {
          onError(event.content ?? '未知错误')
        }
      } catch {
        // ignore malformed events
      }
    }
  }
}

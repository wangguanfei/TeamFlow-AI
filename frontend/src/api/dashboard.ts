import http from './request'

export interface DashboardOverview {
  userCount: number
  projectCount: number
  taskCount: number
  doneTaskCount: number
  knowledgeDocCount: number
  aiMessageCount: number
}

export interface ChartPoint {
  name: string
  value: number
}

export interface DashboardTodoItem {
  id: number
  taskNo: string
  title: string
  status: string
  projectName?: string
  assigneeName?: string
  dueTime?: string
}

export function dashboardOverviewApi() {
  return http.get<unknown, DashboardOverview>('/dashboard/overview')
}

export function projectTrendApi() {
  return http.get<unknown, ChartPoint[]>('/dashboard/project-trend')
}

export function memberActiveApi() {
  return http.get<unknown, ChartPoint[]>('/dashboard/member-active')
}

export function aiUsageApi() {
  return http.get<unknown, ChartPoint[]>('/dashboard/ai-usage')
}

export function dashboardTodosApi() {
  return http.get<unknown, DashboardTodoItem[]>('/dashboard/todos')
}

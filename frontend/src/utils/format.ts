export function formatDateTime(value?: string | null, fallback = '-') {
  if (!value) {
    return fallback
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function formatDateRange(start?: string | null, end?: string | null, fallback = '未规划') {
  if (!start && !end) {
    return fallback
  }
  return `${formatDateTime(start, '-')} 至 ${formatDateTime(end, '-')}`
}

/**
 * 全站顶部加载进度条（零依赖）。
 *
 * 任何接口请求开始时显示，完成后收尾。通过 pending 计数支持并发请求，
 * 避免多个请求互相干扰。用于根治「请求大数据量接口时页面停滞、用户以为网站挂了」的问题。
 */

const BAR_ID = 'tf-global-progress'
const TRICKLE_INTERVAL = 300
const DONE_DELAY = 360

let pending = 0
let progress = 0
let trickleTimer: number | undefined
let barEl: HTMLDivElement | null = null

function ensureBar(): HTMLDivElement {
  if (barEl && document.body.contains(barEl)) {
    return barEl
  }
  const existing = document.getElementById(BAR_ID) as HTMLDivElement | null
  if (existing) {
    barEl = existing
    return barEl
  }
  const el = document.createElement('div')
  el.id = BAR_ID
  el.style.cssText = [
    'position:fixed',
    'top:0',
    'left:0',
    'height:3px',
    'width:0',
    'z-index:9999',
    'background:linear-gradient(90deg,#2563EB,#7C3AED)',
    'box-shadow:0 0 8px rgba(37,99,235,0.5)',
    'border-radius:0 2px 2px 0',
    'opacity:0',
    'transition:width 200ms ease,opacity 320ms ease',
    'pointer-events:none'
  ].join(';')
  document.body.appendChild(el)
  barEl = el
  return el
}

function render() {
  const el = ensureBar()
  el.style.opacity = progress > 0 && progress < 100 ? '1' : progress >= 100 ? '0' : '0'
  el.style.width = `${progress}%`
}

function startTrickle() {
  stopTrickle()
  trickleTimer = window.setInterval(() => {
    // 缓慢逼近 90%，给用户「仍在加载」的持续反馈，但不假装完成
    if (progress < 90) {
      progress = Math.min(90, progress + (90 - progress) * 0.18 + 1)
      render()
    }
  }, TRICKLE_INTERVAL)
}

function stopTrickle() {
  if (trickleTimer) {
    window.clearInterval(trickleTimer)
    trickleTimer = undefined
  }
}

/** 标记一个请求开始 */
export function startProgress(): void {
  pending += 1
  if (pending === 1) {
    progress = 8
    render()
    startTrickle()
  }
}

/** 标记一个请求结束（无论成功或失败都要调用） */
export function doneProgress(): void {
  if (pending > 0) {
    pending -= 1
  }
  if (pending > 0) {
    return
  }
  stopTrickle()
  progress = 100
  render()
  window.setTimeout(() => {
    if (pending === 0) {
      progress = 0
      render()
    }
  }, DONE_DELAY)
}

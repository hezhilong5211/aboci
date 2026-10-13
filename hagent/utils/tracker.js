/**
 * 移动端埋点 SDK
 *
 * 轻量级事件采集，自动批量上报。
 *
 * 使用方式：
 *   import { track, initTracker } from '@/utils/tracker'
 *   initTracker()  // 在 App.vue onLaunch 中调用
 *   track('page_view', { page: '/pages/index' })
 */

const EVENT_QUEUE = []
const FLUSH_INTERVAL = 30000 // 30 秒自动上报
const MAX_BATCH_SIZE = 50

let _timer = null

/**
 * 初始化埋点
 */
export function initTracker() {
  // 自动采集页面访问
  const originalOnShow = Page.prototype.onShow
  Page.prototype.onShow = function () {
    track('page_view', { page: this.route || '' })
    if (originalOnShow) originalOnShow.call(this)
  }

  // 定时上报
  _timer = setInterval(flush, FLUSH_INTERVAL)

  // 应用退出时上报
  uni.$on('appHide', flush)
}

/**
 * 记录事件
 */
export function track(eventName, params = {}) {
  const info = uni.getSystemInfoSync()

  EVENT_QUEUE.push({
    event_name: eventName,
    page: getCurrentPage(),
    params,
    platform: info.platform,
    device_model: info.model,
    app_version: info.appVersion || '',
    user_id: uni.getStorageSync('userId') || null,
  })

  // 超过上限自动上报
  if (EVENT_QUEUE.length >= MAX_BATCH_SIZE) {
    flush()
  }
}

/**
 * 批量上报
 */
export function flush() {
  if (EVENT_QUEUE.length === 0) return

  const batch = EVENT_QUEUE.splice(0, EVENT_QUEUE.length)

  uni.request({
    url: `${getBaseUrl()}/api/mobile/event/batch`,
    method: 'POST',
    header: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${uni.getStorageSync('token') || ''}`,
    },
    data: { events: batch },
    fail: () => {
      // 上报失败回插队列（不超上限）
      if (EVENT_QUEUE.length < MAX_BATCH_SIZE * 2) {
        EVENT_QUEUE.push(...batch)
      }
    },
  })
}

function getCurrentPage() {
  const pages = getCurrentPages()
  return pages.length > 0 ? pages[pages.length - 1].route || '' : ''
}

function getBaseUrl() {
  return uni.getStorageSync('apiBaseUrl') || ''
}

/**
 * 移动端异常上报工具
 *
 * 自动捕获 JS 异常和 Promise 未处理拒绝，
 * 批量上报到后端 /api/monitor/report 接口。
 */

const ERROR_QUEUE = []
const REPORT_INTERVAL = 10000 // 10 秒批量上报
const MAX_QUEUE_SIZE = 50

/**
 * 初始化异常监控
 */
export function initErrorReporter() {
  // 捕获 Vue 组件异常
  const app = getApp()
  if (app && app.$vm) {
    app.$vm.$options.errorHandler = (err, instance, info) => {
      pushError({
        message: err?.message || String(err),
        stackTrace: err?.stack || '',
        extra: { info },
      })
    }
  }

  // 捕获全局 JS 异常
  // #ifdef APP-PLUS
  plus.globalEvent.addEventListener('error', (e) => {
    pushError({
      message: e.message || '未知异常',
      stackTrace: e.filename ? `${e.filename}:${e.lineno}` : '',
    })
  })
  // #endif

  // 启动定时上报
  setInterval(flushErrors, REPORT_INTERVAL)
}

/**
 * 主动上报异常
 */
export function reportError(message, extra = {}) {
  pushError({ message, extra })
}

/**
 * 推入异常队列
 */
function pushError(data) {
  if (ERROR_QUEUE.length >= MAX_QUEUE_SIZE) {
    ERROR_QUEUE.shift() // 丢弃最旧的
  }

  ERROR_QUEUE.push({
    type: 'mobile',
    level: data.level || 'error',
    message: data.message || '未知异常',
    stackTrace: data.stackTrace || '',
    url: getCurrentPageRoute(),
    userAgent: getDeviceInfo(),
    extra: data.extra || {},
    time: new Date().toISOString(),
  })
}

/**
 * 批量上报
 */
function flushErrors() {
  if (ERROR_QUEUE.length === 0) return

  const batch = ERROR_QUEUE.splice(0, ERROR_QUEUE.length)

  batch.forEach((item) => {
    uni.request({
      url: `${getBaseUrl()}/api/monitor/report`,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${uni.getStorageSync('token') || ''}`,
      },
      data: item,
      fail: () => {
        // 上报失败静默处理
      },
    })
  })
}

/**
 * 获取当前页面路由
 */
function getCurrentPageRoute() {
  const pages = getCurrentPages()
  if (pages.length > 0) {
    return pages[pages.length - 1].route || ''
  }
  return ''
}

/**
 * 获取设备信息
 */
function getDeviceInfo() {
  try {
    const info = uni.getSystemInfoSync()
    return `${info.platform} ${info.system} | ${info.model}`
  } catch (e) {
    return 'unknown'
  }
}

/**
 * 获取 API 基础地址
 */
function getBaseUrl() {
  // 根据项目配置获取
  return uni.getStorageSync('apiBaseUrl') || ''
}

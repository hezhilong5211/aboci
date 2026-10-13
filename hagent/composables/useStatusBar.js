import { ref } from 'vue'

// 模块级缓存：只获取一次 statusBarHeight
let _initialized = false
const statusBarHeight = ref(44)

if (!_initialized) {
  _initialized = true
  uni.getSystemInfo({
    success: (info) => {
      statusBarHeight.value = info.statusBarHeight || 44
    }
  })
}

/**
 * 状态栏高度 composable
 * - 只获取一次系统信息，缓存到模块变量
 * - 返回响应式的 statusBarHeight ref
 */
export function useStatusBar() {
  return { statusBarHeight }
}
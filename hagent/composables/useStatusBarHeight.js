import { ref } from 'vue'

/** 优先使用新版窗口 API 获取状态栏高度，低版基础库才回退 getSystemInfo。 */
export function useStatusBarHeight(defaultHeight = 44) {
	const statusBarHeight = ref(defaultHeight)

	try {
		if (typeof uni.getWindowInfo === 'function') {
			const info = uni.getWindowInfo()
			statusBarHeight.value = info?.statusBarHeight || defaultHeight
			return statusBarHeight
		}
	} catch {
		// 低版基础库继续走兼容分支。
	}

	if (typeof uni.getSystemInfo === 'function') {
		uni.getSystemInfo({
			success: (info) => {
				statusBarHeight.value = info.statusBarHeight || defaultHeight
			},
		})
	}

	return statusBarHeight
}

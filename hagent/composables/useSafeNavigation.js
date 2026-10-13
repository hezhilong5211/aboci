import { useTabbarStore } from '@/store/tabbar.js'

/**
 * 安全返回上一页；开发者工具直接把业务页作为首页时，回退到首页容器的指定 Tab。
 */
export function useSafeNavigation(fallbackTab = 'profile') {
	const tabbarStore = useTabbarStore()

	function fallbackToMain() {
		tabbarStore.switchTo(fallbackTab)
		uni.switchTab({ url: '/pages/index/index' })
	}

	function backOrFallback() {
		const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
		if (pages.length > 1) {
			uni.navigateBack({ fail: fallbackToMain })
			return
		}
		fallbackToMain()
	}

	return { backOrFallback }
}

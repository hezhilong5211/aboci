/**
 * 受性能策略控制的资源预加载。
 * 默认轻量模式不主动预加载，避免开发机和低端设备额外发热。
 */

import { onMounted } from 'vue'

export function useAssetPreload(assets = [], options = {}) {
	const {
		enabled = false,
		maxCount = 3,
	} = options

	function preloadImage(src) {
		if (!src) return
		uni.getImageInfo({
			src,
			fail: () => {},
		})
	}

	function preload() {
		if (!enabled) return
		assets.slice(0, maxCount).forEach(preloadImage)
	}

	onMounted(() => {
		preload()
	})

	return {
		preload,
	}
}

/**
 * 移动端性能模式
 * 统一输出页面性能 class 和视觉开关，页面不直接判断平台。
 */

import { computed, ref } from 'vue'
import {
	DEFAULT_PERFORMANCE_MODE,
	PERFORMANCE_MODE,
	getPerformanceOptions,
	mergePlatformPerformanceOptions,
} from '@/config/performance.js'
import { usePlatformOptimize } from '@/composables/usePlatformOptimize.js'

export function usePerformanceMode() {
	const mode = ref(DEFAULT_PERFORMANCE_MODE)
	const {
		optimizeClass,
		optimizeOptions,
		systemInfo: platformSystemInfo,
	} = usePlatformOptimize()
	// 复用 usePlatform 基于 getDeviceInfo/getAppBaseInfo 的缓存，不再调用废弃的 getSystemInfoSync。
	const systemInfo = computed(() => platformSystemInfo)
	const isLightMode = computed(() => mode.value === PERFORMANCE_MODE.LIGHT)
	const visualOptions = computed(() => mergePlatformPerformanceOptions(
		getPerformanceOptions(mode.value),
		optimizeOptions.value
	))
	const pagePerformanceClass = computed(() => (
		[
			isLightMode.value ? 'am-performance-light' : 'am-performance-standard',
			optimizeClass.value,
		].join(' ')
	))

	return {
		mode,
		systemInfo,
		isLightMode,
		visualOptions,
		pagePerformanceClass,
		optimizeOptions,
	}
}

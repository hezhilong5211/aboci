import { computed } from 'vue'
import { getPlatformOptimizeOptions } from '@/config/platform.js'
import { usePlatform } from '@/composables/usePlatform.js'

export function usePlatformOptimize() {
	const platform = usePlatform()

	const optimizeOptions = computed(() => getPlatformOptimizeOptions({
		isIos: platform.isIos.value,
		isAndroid: platform.isAndroid.value,
		isMp: platform.isMp.value,
		isHarmony: platform.isHarmony.value,
	}))

	const optimizeClass = computed(() => [
		'am-platform-optimized',
		platform.isIos.value ? 'am-platform-ios' : '',
		platform.isAndroid.value ? 'am-platform-android' : '',
		platform.isMp.value ? 'am-platform-mp' : '',
		platform.isHarmony.value ? 'am-platform-harmony' : '',
		`am-motion-${optimizeOptions.value.motionLevel}`,
	].filter(Boolean).join(' '))

	return {
		...platform,
		optimizeOptions,
		optimizeClass,
	}
}

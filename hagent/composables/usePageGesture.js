import { ref } from 'vue'
import { usePlatformOptimize } from '@/composables/usePlatformOptimize.js'

export function usePageGesture(options = {}) {
	const {
		enabled = true,
		threshold = 88,
		edgeWidth = 60,
		onBack,
	} = options

	const startX = ref(0)
	const startY = ref(0)
	const tracking = ref(false)
	const { optimizeOptions } = usePlatformOptimize()

	function handleTouchStart(event) {
		if (!enabled || !optimizeOptions.value.enableSwipeBack) return
		const touch = event.touches && event.touches[0]
		if (!touch || touch.clientX > edgeWidth) return
		startX.value = touch.clientX
		startY.value = touch.clientY
		tracking.value = true
	}

	function handleTouchEnd(event) {
		if (!tracking.value) return
		tracking.value = false
		const touch = event.changedTouches && event.changedTouches[0]
		if (!touch) return
		const deltaX = touch.clientX - startX.value
		const deltaY = Math.abs(touch.clientY - startY.value)
		if (deltaX < threshold || deltaY > 80) return
		if (typeof onBack === 'function') {
			onBack()
			return
		}
		uni.navigateBack()
	}

	return {
		gestureProps: {
			onTouchstart: handleTouchStart,
			onTouchend: handleTouchEnd,
		},
		handleTouchStart,
		handleTouchEnd,
	}
}

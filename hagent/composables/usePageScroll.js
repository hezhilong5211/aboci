import { computed, onMounted } from 'vue'
import { buildBottomSpace, SCROLL_LOWER_THRESHOLD } from '@/config/layout.js'
import { usePlatform } from '@/composables/usePlatform.js'

export function usePageScroll(options = {}) {
	const {
		tabbar = false,
		inputBar = false,
		extraBottom = '0px',
		enhanced = true,
		bounces = false,
	} = options

	const { supportsEnhancedScroll, isAppPlus } = usePlatform()

	const scrollEnhanced = computed(() => enhanced && supportsEnhancedScroll.value)
	const scrollBounces = bounces
	const scrollShowScrollbar = false
	const scrollLowerThreshold = SCROLL_LOWER_THRESHOLD

	const scrollProps = computed(() => ({
		'scroll-y': true,
		enhanced: scrollEnhanced.value,
		bounces,
		'show-scrollbar': false,
		'lower-threshold': SCROLL_LOWER_THRESHOLD,
	}))

	const pageScrollClass = computed(() => 'am-page-scroll')

	const contentStyle = computed(() => ({
		paddingBottom: buildBottomSpace({ tabbar, inputBar, extra: extraBottom }),
	}))

	function disablePageBounce() {
		if (!isAppPlus.value) return
		// #ifdef APP-PLUS
		const webview = plus.webview.currentWebview()
		webview && webview.setStyle({ bounce: 'none' })
		// #endif
	}

	onMounted(() => {
		disablePageBounce()
	})

	return {
		scrollProps,
		scrollEnhanced,
		scrollBounces,
		scrollShowScrollbar,
		scrollLowerThreshold,
		pageScrollClass,
		contentStyle,
		disablePageBounce,
	}
}

/**
 * 平台级优化策略。
 * 只定义能力和视觉策略，不在页面里散落平台判断。
 */

// webview 允许加载的域名白名单
export const WEBVIEW_ALLOWED_DOMAINS = [
	// 业务域名（按实际需要配置）
	'wxagentshow.mguit.com',
	'adminyii2.bytesoaring.com',
	'bytesoaring.com',
	// 可根据业务需要在此添加更多域名
]

export const PLATFORM_OPTIMIZE_OPTIONS = {
	default: {
		enableSwipeBack: true,
		enableSmoothPopup: true,
		enableBlurMask: false,
		enablePreload: false,
		motionLevel: 'light',
	},
	ios: {
		enableSwipeBack: true,
		enableSmoothPopup: true,
		enableBlurMask: false,
		enablePreload: false,
		motionLevel: 'light',
	},
	android: {
		enableSwipeBack: false,
		enableSmoothPopup: true,
		enableBlurMask: false,
		enablePreload: false,
		motionLevel: 'light',
	},
	mp: {
		enableSwipeBack: false,
		enableSmoothPopup: false,
		enableBlurMask: false,
		enablePreload: false,
		motionLevel: 'none',
	},
	harmony: {
		enableSwipeBack: false,
		enableSmoothPopup: false,
		enableBlurMask: false,
		enablePreload: false,
		motionLevel: 'none',
	},
}

export function getPlatformOptimizeOptions(flags = {}) {
	if (flags.isHarmony) return PLATFORM_OPTIMIZE_OPTIONS.harmony
	if (flags.isMp) return PLATFORM_OPTIMIZE_OPTIONS.mp
	if (flags.isIos) return PLATFORM_OPTIMIZE_OPTIONS.ios
	if (flags.isAndroid) return PLATFORM_OPTIMIZE_OPTIONS.android
	return PLATFORM_OPTIMIZE_OPTIONS.default
}

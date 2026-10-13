/**
 * 移动端性能配置
 * 页面和组件只消费这里的配置，避免平台判断散落在各处。
 */

export const PERFORMANCE_MODE = {
	STANDARD: 'standard',
	LIGHT: 'light',
}

export const DEFAULT_PERFORMANCE_MODE = PERFORMANCE_MODE.LIGHT

export const PERFORMANCE_OPTIONS = {
	[PERFORMANCE_MODE.STANDARD]: {
		enableFixedBackgroundImage: true,
		enableHeavyDecoration: true,
		enableDeepShadow: true,
		enableTransition: true,
		enableBlurMask: true,
		enablePreload: true,
		motionLevel: 'standard',
	},
	[PERFORMANCE_MODE.LIGHT]: {
		enableFixedBackgroundImage: false,
		enableHeavyDecoration: false,
		enableDeepShadow: false,
		enableTransition: false,
		enableBlurMask: false,
		enablePreload: false,
		motionLevel: 'light',
	},
}

export function getPerformanceOptions(mode = DEFAULT_PERFORMANCE_MODE) {
	return PERFORMANCE_OPTIONS[mode] || PERFORMANCE_OPTIONS[DEFAULT_PERFORMANCE_MODE]
}

export function mergePlatformPerformanceOptions(options, platformOptions = {}) {
	return {
		...options,
		...platformOptions,
	}
}

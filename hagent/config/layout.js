/**
 * 移动端全局布局常量。
 * 统一滚动底部留白，避免各页面重复手写导致底部被吞或需要多次滑动。
 */

export const PAGE_BOTTOM_GAP = '10px'

export const TABBAR_HEIGHT = '100rpx'

export const SCROLL_LOWER_THRESHOLD = 24

export const DEFAULT_NAV_HEIGHT = '88rpx'

export const DEFAULT_NAV_HEIGHT_PX = 44

export function buildBottomSpace({ tabbar = false, inputBar = false, extra = '0px' } = {}) {
	const parts = [PAGE_BOTTOM_GAP, extra]
	if (tabbar) {
		parts.push(TABBAR_HEIGHT, 'env(safe-area-inset-bottom)')
	}
	if (inputBar) {
		parts.push('104rpx', 'env(safe-area-inset-bottom)')
	}
	return `calc(${parts.join(' + ')})`
}

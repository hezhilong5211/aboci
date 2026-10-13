export const MAIN_PAGE_POOL = [
	{ key: 'chat', text: '对话', pagePath: '/pages/index/index', iconPath: '', selectedIconPath: '', visible: true, sort: 0 },
	{ key: 'create', text: '生成', pagePath: '/pages/create/index', iconPath: '', selectedIconPath: '', visible: true, sort: 1 },
	{ key: 'cases', text: '案例', pagePath: '/pages/cases/index', iconPath: '', selectedIconPath: '', visible: true, sort: 2 },
	{ key: 'profile', text: '我的', pagePath: '/pages/user/index', iconPath: '', selectedIconPath: '', visible: true, sort: 3 },
]

export const MAIN_PAGE_OPTIONS = MAIN_PAGE_POOL.map((item) => ({
	label: item.text,
	value: item.pagePath,
}))

export function buildDefaultTabbarConfig() {
	return {
		color: '#999999',
		selectedColor: '#4C6EF5',
		backgroundColor: '#ffffff',
		list: MAIN_PAGE_POOL.map((item) => ({ ...item })),
	}
}

export function buildSafeTabbarConfig() {
	return {
		color: '#999999',
		selectedColor: '#4C6EF5',
		backgroundColor: '#ffffff',
		list: MAIN_PAGE_POOL.map((item) => ({
			...item,
			visible: ['chat', 'create', 'cases', 'profile'].includes(item.key),
		})),
	}
}

function normalizePath(path) {
	if (!path) return ''
	return path.startsWith('/') ? path : `/${path}`
}

function isEnabled(item) {
	const visible = item.visible
	const enabled = item.enabled
	const status = item.status

	if (visible === false || visible === 0 || visible === '0') return false
	if (enabled === false || enabled === 0 || enabled === '0') return false
	if (status === false || status === 0 || status === '0' || status === '1') return false
	return true
}

function normalizeSort(value, fallback) {
	const numberValue = Number(value)
	return Number.isFinite(numberValue) ? numberValue : fallback
}

export function normalizeTabbarConfig(rawConfig) {
	const safeConfig = buildSafeTabbarConfig()
	if (!rawConfig || !Array.isArray(rawConfig.list)) {
		return safeConfig
	}

	const configMap = new Map(rawConfig.list.map((item) => [normalizePath(item.pagePath), item]))

	return {
		color: rawConfig.color || safeConfig.color,
		selectedColor: rawConfig.selectedColor || safeConfig.selectedColor,
		backgroundColor: rawConfig.backgroundColor || safeConfig.backgroundColor,
		list: MAIN_PAGE_POOL.map((item) => {
			const customItem = configMap.get(item.pagePath) || {}
			const hasConfig = configMap.has(item.pagePath)
			return {
				...item,
				text: customItem.text || item.text,
				iconPath: customItem.iconPath || item.iconPath,
				selectedIconPath: customItem.selectedIconPath || item.selectedIconPath,
				visible: hasConfig ? isEnabled(customItem) : false,
				sort: normalizeSort(customItem.sort, item.sort),
			}
		}),
	}
}

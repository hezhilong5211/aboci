import { computed, ref } from 'vue'

const systemInfo = ref(null)

function ensureSystemInfo() {
	if (systemInfo.value) return systemInfo.value
	const info = {}
	try {
		// 基础库 3.7.0+ 优先使用拆分后的新 API，避免 getSystemInfoSync 废弃警告。
		if (typeof uni.getDeviceInfo === 'function') {
			Object.assign(info, uni.getDeviceInfo() || {})
		}
		if (typeof uni.getAppBaseInfo === 'function') {
			Object.assign(info, uni.getAppBaseInfo() || {})
		}
	} catch (e) {
		// 低版基础库继续走兼容分支。
	}
	if (!Object.keys(info).length) {
		try {
			Object.assign(info, uni.getSystemInfoSync())
		} catch (e) {
			// 无法读取时使用空对象兜底。
		}
	}
	systemInfo.value = info
	return systemInfo.value
}

export function usePlatform() {
	const info = ensureSystemInfo()
	const platform = String(info.platform || '').toLowerCase()
	const uniPlatform = String(info.uniPlatform || '').toLowerCase()
	const system = String(info.system || '').toLowerCase()
	const brand = String(info.brand || '').toLowerCase()
	const model = String(info.model || '').toLowerCase()
	const isWechatMiniProgram = typeof wx !== 'undefined'
	const hasAppRuntime = typeof plus !== 'undefined'

	const isIos = computed(() => platform === 'ios' || system.includes('ios'))
	const isAndroid = computed(() => platform === 'android' || system.includes('android'))
	const isHarmony = computed(() => platform.includes('harmony') || system.includes('harmony'))
	const isDevtools = computed(() => platform === 'devtools'
		|| brand === 'devtools'
		|| model.includes('devtools'))
	const isMp = computed(() => isWechatMiniProgram || uniPlatform.startsWith('mp-'))
	const isAppPlus = computed(() => hasAppRuntime || uniPlatform === 'app' || uniPlatform === 'app-plus')
	const supportsEnhancedScroll = computed(() => isMp.value || isAppPlus.value)
	const supportsSwipeBack = computed(() => isIos.value || isAppPlus.value)
	const needsLightMotion = computed(() => isAndroid.value || isMp.value || isHarmony.value)

	return {
		systemInfo: info,
		isIos,
		isAndroid,
		isHarmony,
		isDevtools,
		isMp,
		isAppPlus,
		supportsEnhancedScroll,
		supportsSwipeBack,
		needsLightMotion,
	}
}

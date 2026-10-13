import config from '@/utils/config.js'
import { bindDistributionAttribution, recordDistributionVisit } from '@/api/distribution.js'
import { getStorage, removeStorage, setStorage } from '@/utils/storage.js'

const PENDING_KEY = 'distribution_pending_attribution'
const RECENT_WINDOW = 3000
let recentLaunch = null

function createEventId() {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
		const value = Math.floor(Math.random() * 16)
		const result = char === 'x' ? value : (value & 0x3) | 0x8
		return result.toString(16)
	})
}

function parseInviteCode(options = {}) {
	const query = options.query || {}
	if (query.d) return String(query.d).trim()
	if (!query.scene) return ''
	let scene = String(query.scene)
	try {
		scene = decodeURIComponent(scene)
	} catch (e) {
		// scene 不是编码字符串时直接按原值解析。
	}
	const params = scene.split('&')
	for (const param of params) {
		const index = param.indexOf('=')
		if (index > 0 && param.slice(0, index) === 'd') {
			return param.slice(index + 1).trim()
		}
	}
	return ''
}

function launchScene(options = {}) {
	return options.scene == null ? '' : String(options.scene)
}

export async function captureDistributionLaunch(options = {}) {
	const inviteCode = parseInviteCode(options)
	if (!inviteCode) return

	const now = Date.now()
	if (recentLaunch && recentLaunch.inviteCode === inviteCode && now - recentLaunch.time < RECENT_WINDOW) {
		return
	}
	recentLaunch = { inviteCode, time: now }

	const pending = {
		inviteCode,
		eventId: createEventId(),
		launchScene: launchScene(options),
	}
	setStorage(PENDING_KEY, pending)
	try {
		await recordDistributionVisit(pending)
	} catch (e) {
		return
	}
	await flushDistributionAttribution()
}

export async function flushDistributionAttribution() {
	const pending = getStorage(PENDING_KEY)
	const token = getStorage(config.tokenKey)
	if (!pending || !token) return false

	// 避免微信二次转译压缩产物时将 try 块变量提升，遮蔽模块依赖。
	let result
	try {
		result = await bindDistributionAttribution({
			inviteCode: pending.inviteCode,
			eventId: pending.eventId,
		})
		if (result?.bound || result?.alreadyBound) {
			removeStorage(PENDING_KEY)
			return true
		}
	} catch (e) {
		// 暂时失败时保留待绑定数据，下次登录或启动继续尝试。
	}
	return false
}

import config from '@/utils/config.js'

/**
 * 统一存储封装
 * 对 uni.setStorageSync / getStorageSync 进行二次封装
 * 统一管理存储键名，避免硬编码
 */

/**
 * 设置缓存
 * @param {string} key - 存储键名
 * @param {any} value - 存储值
 */
export function setStorage(key, value) {
	try {
		uni.setStorageSync(key, value)
	} catch (e) {
		console.error(`[Storage] 写入失败: ${key}`, e)
	}
}

/**
 * 获取缓存
 * @param {string} key - 存储键名
 * @param {any} defaultValue - 默认值
 * @returns {any}
 */
export function getStorage(key, defaultValue = null) {
	try {
		const value = uni.getStorageSync(key)
		return value !== '' && value !== undefined ? value : defaultValue
	} catch (e) {
		console.error(`[Storage] 读取失败: ${key}`, e)
		return defaultValue
	}
}

/**
 * 移除缓存
 * @param {string} key - 存储键名
 */
export function removeStorage(key) {
	try {
		uni.removeStorageSync(key)
	} catch (e) {
		console.error(`[Storage] 删除失败: ${key}`, e)
	}
}

/**
 * 清空所有缓存
 */
export function clearStorage() {
	try {
		uni.clearStorageSync()
	} catch (e) {
		console.error('[Storage] 清空失败', e)
	}
}

/** 清理普通缓存时保留登录凭证，不先清空再回写，避免中途失败丢失会话。 */
export function clearNonAuthStorage() {
	const retained = new Set([config.tokenKey, config.refreshTokenKey, config.userInfoKey])
	const { keys } = uni.getStorageInfoSync()
	keys.filter((key) => !retained.has(key)).forEach((key) => uni.removeStorageSync(key))
}

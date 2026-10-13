/**
 * 请求核心封装
 * 基于 uni.request 二次封装，统一处理：
 * 1. 自动注入 Authorization 请求头
 * 2. 统一解析 {code, msg, data} 响应格式
 * 3. 401 自动跳转登录页
 * 4. 请求/响应错误统一提示
 */

import config from '@/utils/config.js'
import { getStorage, setStorage, removeStorage } from '@/utils/storage.js'
import { navigateToLogin } from '@/utils/navigate.js'

export const AUTH_EXPIRED_EVENT = 'auth-expired'
export const AUTH_TOKEN_REFRESHED_EVENT = 'auth-token-refreshed'

// 同一登录会话共用续期请求；登录/退出时递增版本，隔离旧会话的迟到响应。
let refreshFlight = null
let authRevision = 0
let logoutScheduled = false
// refreshToken 持久化到 storage（小程序 storage 为应用沙箱，无 web XSS 窃取面），支持冷启动静默续签

/**
 * 发起请求
 * @param {Object} options - 请求配置
 * @param {string} options.url - 请求路径（相对路径，自动拼接 baseUrl）
 * @param {string} [options.method='GET'] - 请求方法
 * @param {Object} [options.data] - 请求数据
 * @param {Object} [options.header] - 自定义请求头
 * @param {boolean} [options.showLoading=false] - 是否显示加载提示
 * @param {boolean} [options.showError=true] - 是否显示错误提示
 * @param {boolean} [options.skipAuthRefresh=false] - 诊断类请求可禁止刷新失败后跳转登录
 * @returns {Promise<any>} - 返回 data 字段
 */
function request(options = {}) {
	const {
		url,
		method = 'GET',
		data = {},
		header = {},
		showLoading = false,
		showError = true,
		skipAuthRefresh = false
	} = options

	return new Promise((resolve, reject) => {
		// 显示加载提示
		if (showLoading) {
			uni.showLoading({ title: '加载中...', mask: true })
		}

		// 构建请求头
		const requestHeader = {
			'Content-Type': 'application/json',
			...header
		}

		// 自动注入 token
		const token = getStorage(config.tokenKey)
		const requestRevision = authRevision
		if (token) {
			requestHeader['Authorization'] = `Bearer ${token}`
		}

		uni.request({
			url: `${config.baseUrl}${url}`,
			method: method.toUpperCase(),
			data,
			header: requestHeader,
			timeout: config.timeout,
			success: (res) => {
				if (showLoading) uni.hideLoading()

				const statusCode = res.statusCode
				const responseData = res.data

				if (statusCode === 401 || (statusCode === 200 && responseData?.code === 401)) {
					if (skipAuthRefresh) {
						reject(responseData)
					} else {
						handleUnauthorized(options, token, requestRevision).then(resolve, reject)
					}
					return
				}

				// HTTP 状态码异常
				if (statusCode !== 200) {
					handleHttpError(statusCode, responseData, showError)
					reject(responseData)
					return
				}

				// 业务状态码判断
				const { code, msg, data: resData } = responseData || {}

				if (code === 200) {
					// 请求成功，返回 data 字段
					resolve(resData)
				} else {
					// 其他业务错误
					// 带页面动作的业务错误由调用页展示可点击交互，避免先弹普通 Toast。
					const hasPageAction = resData && typeof resData === 'object' && resData.action
					if (showError && !hasPageAction) {
						uni.showToast({
							title: msg || '操作失败',
							icon: 'none',
							duration: 2000
						})
					}
					reject(responseData)
				}
			},
			fail: (err) => {
				if (showLoading) uni.hideLoading()
				if (showError) {
					uni.showToast({
						title: '网络异常，请稍后重试',
						icon: 'none',
						duration: 2000
					})
				}
				reject(err)
			}
		})
	})
}

/**
 * HTTP 错误处理
 */
function handleHttpError(statusCode, responseData, showError) {
	let message = '请求失败'
	switch (statusCode) {
		case 400:
			message = responseData?.msg || '参数错误'
			break
		case 403:
			message = '没有权限'
			break
		case 404:
			message = '请求地址不存在'
			break
		case 429:
			message = '请求过于频繁，请稍后再试'
			break
		case 500:
			message = responseData?.msg || '服务器错误'
			break
		default:
			message = `请求失败(${statusCode})`
	}
	if (showError) {
		uni.showToast({ title: message, icon: 'none', duration: 2000 })
	}
}

/** 旧请求的 401 只重试一次，不重复轮换已经更新的令牌。 */
async function handleUnauthorized(options, sentToken, requestRevision) {
	if (requestRevision !== authRevision) throw { code: 409, msg: '登录状态已变更' }
	if (options._authRetried || options.url === '/api/mobile/refreshToken') {
		throw { code: 401, msg: '请求未通过身份验证' }
	}
	const currentToken = getStorage(config.tokenKey)
	if (!currentToken || currentToken === sentToken) {
		await refreshAccessToken(options.showError !== false)
	}
	if (requestRevision !== authRevision) throw { code: 409, msg: '登录状态已变更' }
	return request({ ...options, _authRetried: true })
}

function refreshAccessToken(notify) {
	const refreshToken = getRefreshToken()
	if (!refreshToken) {
		forceLogout()
		return Promise.reject({ code: 401, msg: '登录已过期' })
	}
	if (refreshFlight?.token === refreshToken && refreshFlight.revision === authRevision) {
		refreshFlight.notify ||= notify
		return refreshFlight.promise
	}
	const revision = authRevision
	const flight = { token: refreshToken, revision, notify }
	const isCurrentSession = () => revision === authRevision && getRefreshToken() === refreshToken
	flight.promise = new Promise((resolve, reject) => {
		uni.request({
			url: `${config.baseUrl}/api/mobile/refreshToken`,
			method: 'POST',
			data: { refreshToken },
			header: { 'Content-Type': 'application/json' },
			timeout: config.timeout,
			success: (res) => {
				if (!isCurrentSession()) {
					reject({ code: 409, msg: '登录状态已变更' })
					return
				}
				const body = res.data
				const pair = body?.data
				if (res.statusCode === 200 && body?.code === 200
					&& typeof pair?.token === 'string' && pair.token
					&& typeof pair?.refreshToken === 'string' && pair.refreshToken) {
					try {
						// 先保存可用于恢复的 refresh；写入失败必须向调用方报告。
						uni.setStorageSync(config.refreshTokenKey, pair.refreshToken)
						uni.setStorageSync(config.tokenKey, pair.token)
						uni.$emit(AUTH_TOKEN_REFRESHED_EVENT, pair.token)
						resolve(pair.token)
					} catch (error) {
						reject({ code: 'AUTH_STORAGE_FAILED', msg: '登录状态保存失败，请稍后重试' })
					}
					return
				}
				// 兼容旧后端将明确的刷新令牌失效放在业务 500 中；普通 5xx/429 不是退出理由。
				const legacyInvalid = body?.code === 500 && [
					'刷新令牌已失效，请重新登录',
					'刷新令牌无效或已过期，请重新登录',
					'令牌类型不正确',
				].includes(body.msg)
				const invalid = res.statusCode === 401
					|| (res.statusCode === 200 && (body?.code === 401 || legacyInvalid))
				if (invalid) forceLogout()
				reject(invalid
					? { code: 401, msg: '登录已过期' }
					: { code: 'AUTH_REFRESH_UNAVAILABLE', msg: '登录续期暂时失败，请稍后重试' })
			},
			fail: () => reject({ code: 'AUTH_REFRESH_UNAVAILABLE', msg: '网络异常，请稍后重试' }),
		})
	}).catch((error) => {
		if (flight.notify && ['AUTH_REFRESH_UNAVAILABLE', 'AUTH_STORAGE_FAILED'].includes(error.code)) {
			uni.showToast({ title: error.msg, icon: 'none', duration: 2000 })
		}
		throw error
	}).finally(() => {
		if (refreshFlight === flight) refreshFlight = null
	})
	refreshFlight = flight
	return flight.promise
}

/**
 * 强制退出登录（清除登录态 + 跳转登录页）
 */
function forceLogout() {
	authRevision += 1
	uni.removeStorageSync(config.tokenKey)
	uni.removeStorageSync(config.userInfoKey)
	uni.removeStorageSync(config.refreshTokenKey)
	// storage 与 Pinia 必须同时失效，否则页面仍会显示旧用户资料，却把请求失败
	// 误表现为某一项业务身份消失。
	uni.$emit(AUTH_EXPIRED_EVENT)

	if (logoutScheduled) return
	logoutScheduled = true

	// 获取当前页面路径作为重定向参数
	const pages = getCurrentPages()
	const currentPage = pages[pages.length - 1]
	const redirect = currentPage ? `/${currentPage.route}` : ''

	uni.showToast({
		title: '登录已过期，请重新登录',
		icon: 'none',
		duration: 1500
	})

	setTimeout(() => {
		logoutScheduled = false
		if (!getStorage(config.tokenKey) && !getRefreshToken()) navigateToLogin(redirect)
	}, 1500)
}

/**
 * 设置 refreshToken（持久化到 storage，支持冷启动续签；传 null 清除）
 */
export function setRefreshToken(token) {
	authRevision += 1
	if (token) {
		setStorage(config.refreshTokenKey, token)
	} else {
		removeStorage(config.refreshTokenKey)
	}
}

/**
 * 获取 refreshToken（从 storage 读取）
 */
export function getRefreshToken() {
	return getStorage(config.refreshTokenKey, null)
}

// 快捷方法
export const get = (url, data = {}, options = {}) =>
	request({ url, method: 'GET', data, ...options })

export const post = (url, data = {}, options = {}) =>
	request({ url, method: 'POST', data, ...options })

export const put = (url, data = {}, options = {}) =>
	request({ url, method: 'PUT', data, ...options })

export const del = (url, data = {}, options = {}) =>
	request({ url, method: 'DELETE', data, ...options })

export default request

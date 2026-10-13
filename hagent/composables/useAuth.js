/**
 * 登录态管理组合式函数
 * 封装登录流程、登录态判断、自动登录等逻辑
 */

import { useUserStore } from '@/store/user.js'
import { setRefreshToken } from '@/api/request.js'
import config from '@/utils/config.js'
import { navigateToLogin } from '@/utils/navigate.js'

/**
 * 登录态管理
 */
export function useAuth() {
	const userStore = useUserStore()

	/**
	 * 检查是否需要登录
	 * 对非白名单页面，未登录则跳转登录页
	 * @param {string} pagePath - 当前页面路径
	 * @returns {boolean} - 是否已登录
	 */
	function checkAuth(pagePath = '') {
		if (config.whiteList.includes(pagePath)) {
			return true
		}
		if (!userStore.isLoggedIn) {
			navigateToLogin(pagePath)
			return false
		}
		return true
	}

	/**
	 * 微信快捷登录流程
	 * 调用 wx.login 获取 code → 发送到后端换 token
	 */
	async function wxQuickLogin() {
		// #ifdef MP-WEIXIN
		return new Promise((resolve, reject) => {
			uni.login({
				provider: 'weixin',
				success: (loginRes) => {
					resolve(loginRes.code)
				},
				fail: (err) => {
					uni.showToast({ title: '微信登录失败', icon: 'none' })
					reject(err)
				}
			})
		})
		// #endif
	}

	/**
	 * 处理登录成功
	 * @param {Object} loginData - { token, userInfo }
	 */
	function handleLoginSuccess(loginData) {
		const { token, refreshToken, userInfo } = loginData
		userStore.setToken(token)
		if (refreshToken) {
			setRefreshToken(refreshToken)
		}
		if (userInfo) {
			userStore.setUserInfo(userInfo)
		}
	}

	/**
	 * 退出登录
	 */
	function handleLogout() {
		userStore.clearAuth()
		// 退出后回首页(游客态),不强制跳登录页;登录由核心功能(对话/生成)按需触发
		uni.reLaunch({ url: '/pages/index/index' })
	}

	return {
		checkAuth,
		wxQuickLogin,
		handleLoginSuccess,
		handleLogout,
		isLoggedIn: userStore.isLoggedIn
	}
}

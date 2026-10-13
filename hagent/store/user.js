/**
 * 用户状态管理
 * 管理 token、用户信息、登录态
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getStorage, setStorage, removeStorage } from '@/utils/storage.js'
import { setRefreshToken as setRefreshTokenMemory } from '@/api/request.js'
import config from '@/utils/config.js'

export const useUserStore = defineStore('user', () => {
	// ====== 状态 ======
	const token = ref(getStorage(config.tokenKey, ''))
	const userInfo = ref(getStorage(config.userInfoKey, null))

	// ====== 计算属性 ======

	/** 是否已登录 */
	const isLoggedIn = computed(() => !!token.value)

	/** 用户昵称 */
	const nickName = computed(() => userInfo.value?.nickName || '')

	/** 用户头像 */
	const avatarUrl = computed(() => userInfo.value?.avatarUrl || '')

	/** 手机号 */
	const phone = computed(() => userInfo.value?.phone || '')

	/** 是否已绑定手机号 */
	const hasPhone = computed(() => !!userInfo.value?.phone)

	/** 是否已设置密码 */
	const hasPassword = computed(() => !!userInfo.value?.hasPassword)

	// ====== 方法 ======

	/**
	 * 设置 token
	 * @param {string} newToken
	 */
	function setToken(newToken) {
		token.value = newToken
		setStorage(config.tokenKey, newToken)
	}

	/**
	 * 设置用户信息
	 * @param {Object} info
	 */
	function setUserInfo(info) {
		userInfo.value = info
		setStorage(config.userInfoKey, info)
	}

	/**
	 * 更新用户信息（局部更新）
	 * @param {Object} partial - 要更新的字段
	 */
	function updateUserInfo(partial) {
		if (userInfo.value) {
			userInfo.value = { ...userInfo.value, ...partial }
			setStorage(config.userInfoKey, userInfo.value)
		}
	}

	/**
	 * 清除登录态（退出登录）
	 */
	function clearAuth() {
		token.value = ''
		userInfo.value = null
		removeStorage(config.tokenKey)
		removeStorage(config.userInfoKey)
		setRefreshTokenMemory(null)
	}

	return {
		token,
		userInfo,
		isLoggedIn,
		nickName,
		avatarUrl,
		phone,
		hasPhone,
		hasPassword,
		setToken,
		setUserInfo,
		updateUserInfo,
		clearAuth
	}
})

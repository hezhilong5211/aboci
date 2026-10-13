/**
 * 登录拦截组合式函数
 * 统一管理「未登录时弹登录弹窗」的全局开关与守卫逻辑。
 *
 * 数据流:
 *  - 写入:requireLogin() —— 各入口(详细页跳转、核心功能、个人中心交互)未登录时调用,置 true
 *  - 读取:pages/index/index.vue 的登录弹窗(单容器架构,全局唯一渲染处)
 *  - 关闭:先逛逛 / 登录成功后调用 hideLoginModal()
 */

import { ref } from 'vue'
import { useUserStore } from '@/store/user.js'

// 模块级状态:整个小程序单例共享。仅在 index.vue 渲染弹窗,避免多处重复弹出。
const loginModalVisible = ref(false)

export function useLoginGuard() {
	const userStore = useUserStore()

	/**
	 * 登录守卫:已登录返回 true;未登录则弹登录弹窗并返回 false。
	 * 用法:if (!requireLogin()) return —— 拦在详细页跳转 / 核心功能调用之前。
	 */
	function requireLogin() {
		if (userStore.isLoggedIn) return true
		loginModalVisible.value = true
		return false
	}

	function openLoginModal() {
		loginModalVisible.value = true
	}

	function hideLoginModal() {
		loginModalVisible.value = false
	}

	return { loginModalVisible, requireLogin, openLoginModal, hideLoginModal }
}

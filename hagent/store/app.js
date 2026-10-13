/**
 * 应用配置状态管理
 * 管理 TabBar 配置、DIY 页面缓存、全局应用设置
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
	// ====== 状态 ======

	/** TabBar 配置（从后端获取） */
	const tabbarConfig = ref(null)

	/** 分享配置（{title, imageUrl},App 启动免鉴权拉取,首页分享钩子读取） */
	const shareConfig = ref(null)

	/** 首页 DIY 页面数据缓存 */
	const homePageData = ref(null)

	/** 是否已初始化（防止重复请求） */
	const initialized = ref(false)

	/** 网络状态 */
	const networkType = ref('unknown')

	// ====== 方法 ======

	/**
	 * 设置 TabBar 配置
	 * @param {Object} config
	 */
	function setTabbarConfig(config) {
		tabbarConfig.value = config
	}

	/**
	 * 设置分享配置
	 * @param {Object} config
	 */
	function setShareConfig(config) {
		shareConfig.value = config
	}

	/**
	 * 设置首页 DIY 数据
	 * @param {Object} data
	 */
	function setHomePageData(data) {
		homePageData.value = data
	}

	/**
	 * 标记已初始化
	 */
	function markInitialized() {
		initialized.value = true
	}

	/**
	 * 更新网络状态
	 * @param {string} type
	 */
	function setNetworkType(type) {
		networkType.value = type
	}

	/**
	 * 重置应用状态
	 */
	function resetApp() {
		tabbarConfig.value = null
		homePageData.value = null
		initialized.value = false
	}

	return {
		tabbarConfig,
		shareConfig,
		homePageData,
		initialized,
		networkType,
		setTabbarConfig,
		setShareConfig,
		setHomePageData,
		markInitialized,
		setNetworkType,
		resetApp
	}
})

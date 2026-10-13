import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { buildSafeTabbarConfig, normalizeTabbarConfig } from '@/config/navigation/main-pages.js'

export const useTabbarStore = defineStore('tabbar', () => {
	const config = ref(buildSafeTabbarConfig())
	const hasSynced = ref(false)
	/** 当前激活的 tab key（与后台配置的 key 对应，如 chat/create/cases/profile） */
	const activeTab = ref('chat')

	function setConfig(data) {
		const next = normalizeTabbarConfig(data)
		config.value = next
		hasSynced.value = true
		ensureActiveVisible(next)
	}

	function useSafeConfig() {
		const next = buildSafeTabbarConfig()
		config.value = next
		ensureActiveVisible(next)
	}

	function ensureActiveVisible(nextConfig) {
		const visible = [...nextConfig.list]
			.filter((item) => item.visible === true)
			.sort((a, b) => a.sort - b.sort)
		if (!visible.some((item) => item.key === activeTab.value) && visible.length) {
			activeTab.value = visible[0].key
		}
	}

	/** 切换 tab（通过 key 标识，不使用 switchTab） */
	function switchTo(key) {
		activeTab.value = key
	}

	return {
		config,
		hasSynced,
		activeTab,
		visibleTabs: computed(() => {
			return [...config.value.list]
				.filter((item) => item.visible === true)
				.sort((a, b) => a.sort - b.sort)
		}),
		setConfig,
		useSafeConfig,
		switchTo,
	}
})

<script>
	import { getTabbarConfig, getShareConfig } from '@/api/common.js'
	import { useTabbarStore } from '@/store/tabbar.js'
	import { useAppStore } from '@/store/app.js'
	import { useUserStore } from '@/store/user.js'
	import { AUTH_EXPIRED_EVENT, AUTH_TOKEN_REFRESHED_EVENT } from '@/api/request.js'
	import appConfig from '@/utils/config.js'
	import { captureDistributionLaunch, flushDistributionAttribution } from '@/utils/distribution-attribution.js'
	let lastTabbarSyncAt = 0

	export default {
		onLaunch: function(options) {
			console.log('[Aurora Mobile] App Launch')
			uni.$off(AUTH_EXPIRED_EVENT, this.handleAuthExpired)
			uni.$off(AUTH_TOKEN_REFRESHED_EVENT, this.handleTokenRefreshed)
			uni.$on(AUTH_EXPIRED_EVENT, this.handleAuthExpired)
			uni.$on(AUTH_TOKEN_REFRESHED_EVENT, this.handleTokenRefreshed)
			void captureDistributionLaunch(options)
			// 不强制登录:进入小程序直接到首页(游客可浏览),由首页弹登录提示弹窗引导「去登录」
			// 自定义 tabBar(pages.json custom:true)下原生栏不渲染,无需 hideTabBar
			// 获取后台 TabBar 配置，更新 store（CustomTabBar 响应式读取）
			this.syncTabbar(true)
			// 获取分享配置(标题/卡片图,免鉴权;首页分享钩子读取,失败静默走微信默认截图)
			this.syncShareConfig()
			// 检查网络状态
			uni.getNetworkType({
				success: (res) => {
					console.log('[Aurora Mobile] 网络类型:', res.networkType)
				}
			})
			// 监听网络状态变化
			uni.onNetworkStatusChange((res) => {
				console.log('[Aurora Mobile] 网络状态变化:', res.isConnected, res.networkType)
				if (!res.isConnected) {
					uni.showToast({ title: '网络已断开', icon: 'none' })
				}
			})
		},
		onShow: function(options) {
			console.log('[Aurora Mobile] App Show')
			void captureDistributionLaunch(options)
			void flushDistributionAttribution()
			this.syncTabbar()
		},
		onHide: function() {
			console.log('[Aurora Mobile] App Hide')
		},
		methods: {
			handleAuthExpired() {
				useUserStore().clearAuth()
			},

			handleTokenRefreshed(token) {
				if (token) useUserStore().setToken(token)
			},

			/** 从后端获取 TabBar 配置并存入 store */
			async syncTabbar(force = false) {
				const now = Date.now()
				if (!force && lastTabbarSyncAt && now - lastTabbarSyncAt < 60000) return
				lastTabbarSyncAt = now
				const tabbarStore = useTabbarStore()
				try {
					const config = await getTabbarConfig()
					if (config && config.list && config.list.length > 0) {
						// 先按远程 URL 立即渲染,再异步把图标本地化后覆盖(切 tab 零网络零闪烁)
						tabbarStore.setConfig(config)
						console.log('[Aurora Mobile] TabBar 配置同步成功')
						const localized = await this.localizeTabbarIcons(config)
						tabbarStore.setConfig(localized)
					} else {
						tabbarStore.useSafeConfig()
						console.log('[Aurora Mobile] TabBar 未返回配置，使用安全兜底')
					}
				} catch (e) {
					if (!tabbarStore.hasSynced) tabbarStore.useSafeConfig()
					console.log('[Aurora Mobile] TabBar 配置获取失败，保留最近一次可用配置', e)
				}
			},

			/** 图标本地化:双态图标下载为本地临时文件,返回新配置对象;单张失败保留原 URL 兜底 */
			async localizeTabbarIcons(config) {
				const list = await Promise.all((config.list || []).map(async (item) => ({
					...item,
					iconPath: await this.downloadIcon(item.iconPath),
					selectedIconPath: await this.downloadIcon(item.selectedIconPath),
				})))
				return { ...config, list }
			},

			/** 拉取分享配置进 store;失败静默(分享钩子有兜底) */
			async syncShareConfig() {
				try {
					const cfg = await getShareConfig()
					if (cfg) useAppStore().setShareConfig(cfg)
				} catch (e) {
					// 静默:未配置/请求失败时分享用默认标题+页面截图
				}
			},

			/** 下载单个图标为本地临时路径;空值/包内 /static 路径/下载失败均原样返回 */
			downloadIcon(url) {
				return new Promise((resolve) => {
					if (!url || url.startsWith('/static')) return resolve(url)
					const full = url.startsWith('http') ? url : appConfig.baseUrl + url
					uni.downloadFile({
						url: full,
						success: (res) => resolve(res.statusCode === 200 && res.tempFilePath ? res.tempFilePath : url),
						fail: () => resolve(url),
					})
				})
			}
		}
	}
</script>

<style lang="scss">
	/* 引入公共样式 */
	@import '@/styles/common.scss';
	@import '@/styles/theme.scss';
	@import '@/styles/motion.scss';

	/* 全局字体：优先使用系统无衬线字体，避免模拟器默认宋体 */
	page {
		font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue',
			'Microsoft YaHei', 'Source Han Sans SC', sans-serif;
	}

	/*
	 * 隐藏原生 TabBar —— CSS 级别强制不可见
	 * switchTab 会让原生 TabBar 短暂重新显示，hideTabBar API 隐藏有延迟
	 * 用 CSS 强制高度为 0 + 不可见，消除切换时的闪烁/跳动
	 */
	.uni-tabbar-bottom {
		height: 0 !important;
		min-height: 0 !important;
		overflow: hidden !important;
		opacity: 0 !important;
		pointer-events: none !important;
	}
</style>

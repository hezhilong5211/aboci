/**
 * 统一导航封装
 * 处理不同跳转类型：内部页面 / TabBar / H5 / 小程序 / 电话
 * DIY 组件的跳转链接统一通过此模块分发
 */

/**
 * 跳转链接对象结构
 * @typedef {Object} LinkConfig
 * @property {string} type - 跳转类型：page / tab / h5 / miniapp / tel
 * @property {string} url - 目标地址
 * @property {string} [appId] - 目标小程序 appId（type=miniapp 时必填）
 * @property {string} [path] - 目标小程序页面路径（type=miniapp 时可选）
 */

/**
 * 统一跳转处理
 * @param {LinkConfig} link - 跳转配置
 */
export function handleNavigate(link) {
	if (!link || !link.type || !link.url) return

	switch (link.type) {
		case 'page':
			// 内部页面跳转
			uni.navigateTo({ url: link.url })
			break

		case 'tab':
			// TabBar 页面切换
			uni.switchTab({ url: link.url })
			break

		case 'h5':
			// H5 链接：小程序端用 web-view，APP 端用内置浏览器
			// #ifdef MP-WEIXIN
			uni.navigateTo({
				url: `/pages/webview/index?url=${encodeURIComponent(link.url)}`
			})
			// #endif
			// #ifdef APP-PLUS
			plus.runtime.openURL(link.url)
			// #endif
			// #ifdef H5
			window.open(link.url)
			// #endif
			break

		case 'miniapp':
			// 跳转其他小程序（仅小程序端生效）
			// #ifdef MP-WEIXIN
			uni.navigateToMiniProgram({
				appId: link.appId || '',
				path: link.path || '',
				fail: (err) => {
					console.error('[Navigate] 跳转小程序失败', err)
				}
			})
			// #endif
			break

		case 'tel':
			// 拨打电话
			uni.makePhoneCall({
				phoneNumber: link.url,
				fail: (err) => {
					console.error('[Navigate] 拨打电话失败', err)
				}
			})
			break

		default:
			console.warn(`[Navigate] 未知跳转类型: ${link.type}`)
	}
}

/**
 * 跳转到登录页
 * @param {string} [redirect] - 登录后重定向路径
 */
export function navigateToLogin(redirect = '') {
	const url = redirect
		? `/pages/login/index?redirect=${encodeURIComponent(redirect)}`
		: '/pages/login/index'
	uni.navigateTo({ url })
}

/**
 * 返回上一页
 * @param {number} [delta=1] - 返回层数
 */
export function navigateBack(delta = 1) {
	uni.navigateBack({ delta })
}

/**
 * 跳转到首页
 */
export function navigateToHome() {
	uni.switchTab({ url: '/pages/index/index' })
}

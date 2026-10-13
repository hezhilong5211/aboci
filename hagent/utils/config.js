/**
 * 环境配置
 * 本地联调与生产 API 地址
 */

// 开发环境 API 地址（微信开发者工具本地联调）
const DEV_BASE_URL = 'http://127.0.0.1:8080'

// 生产环境 API 地址
const PROD_BASE_URL = 'https://wxagentshow.mguit.com'

// HBuilderX 运行时连接本地，发行编译时连接生产域名。
const isDev = process.env.NODE_ENV === 'development'

const config = {
	// API 基础地址
	baseUrl: isDev ? DEV_BASE_URL : PROD_BASE_URL,

	// 请求超时时间（毫秒）
	timeout: 15000,

	// Token 存储键名
	tokenKey: 'auroratoken',

	// 用户信息存储键名
	userInfoKey: 'aurora_user_info',

	// 刷新 Token 存储键名
	refreshTokenKey: 'aurora_refresh_token',

	// 登录页路径
	loginPage: '/pages/login/index',

	// 首页路径
	homePage: '/pages/index/index',

	// 不需要登录的页面白名单
	whiteList: [
		'/pages/login/index',
		'/pages/webview/index'
	]
}

export default config

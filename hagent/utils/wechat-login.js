/** 校验微信身份凭证，避免游客模式的模拟 code 被提交到服务端。 */
export function assertWechatLoginCode(code, { showError = true } = {}) {
	let errorCode = ''
	let message = ''
	if (typeof code !== 'string' || !code.trim()) {
		errorCode = 'WX_LOGIN_EMPTY'
		message = '获取微信登录凭证失败，请重新登录'
	} else if (code === 'the code is a mock one') {
		errorCode = 'WX_LOGIN_MOCK'
		message = '当前为游客测试模式，请配置真实 AppID 后重新编译'
	}
	if (!errorCode) return

	const error = new Error(message)
	error.code = errorCode
	// 与请求层保持相同的错误字段，支付流程也能展示具体原因。
	error.msg = message
	if (showError) {
		uni.showToast({ title: message, icon: 'none', duration: 4000 })
	}
	throw error
}

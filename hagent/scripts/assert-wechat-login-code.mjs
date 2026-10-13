import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'

const root = new URL('../', import.meta.url)
const calls = [], toasts = []
const context = vm.createContext({
	uni: { showToast: (options) => toasts.push(options) },
	post: (...args) => { calls.push(args); return Promise.resolve('ok') },
})
for (const file of ['utils/wechat-login.js', 'api/auth.js', 'api/virtualPay.js']) {
	const source = readFileSync(new URL(file, root), 'utf8')
	vm.runInContext(source.replace(/^import .*$/gm, '').replace(/\bexport /g, ''), context, {
		filename: fileURLToPath(new URL(file, root)),
	})
}

const endpoints = [
	['微信登录', '/api/mobile/wx/login', (code) => context.wxLogin({ code }), 'code', true],
	['手机号登录', '/api/mobile/wx/phoneLogin', (code) => context.wxPhoneLogin({ loginCode: code, phoneCode: 'phone-code' }), 'loginCode', true],
	['微信会话刷新', '/api/mobile/wx/session/refresh', (code) => context.refreshWxSession(code), 'code', false],
]
let passed = 0
for (const [name, url, invoke, field, showError] of endpoints) {
	for (const code of [undefined, null, '', '   ', 'the code is a mock one']) {
		calls.length = 0
		toasts.length = 0
		const result = invoke(code)
		assert.equal(typeof result.then, 'function', `${name} 必须返回 Promise`)
		await assert.rejects(result, (error) => {
			assert.equal(error.code, code === 'the code is a mock one' ? 'WX_LOGIN_MOCK' : 'WX_LOGIN_EMPTY')
			assert.ok(error.msg, '错误必须提供可展示的中文原因')
			return true
		})
		assert.equal(calls.length, 0, `${name} 的无效凭证不得发起请求`)
		assert.equal(toasts.length, showError ? 1 : 0, `${name} 应保留原有错误展示职责`)
		passed++
	}
	// code 是不透明字符串：不推断其长度/格式，不裁剪或重新编码。
	const code = 'test-opaque_Code+12/='
	calls.length = 0
	toasts.length = 0
	assert.equal(await invoke(code), 'ok')
	assert.equal(calls.length, 1)
	assert.equal(calls[0][0], url)
	assert.equal(calls[0][1][field], code)
	if (field === 'loginCode') assert.equal(calls[0][1].phoneCode, 'phone-code')
	if (!showError) assert.equal(calls[0][2].showError, false)
	assert.equal(toasts.length, 0)
	passed++
	console.log(`通过：${name} 拦截空值/模拟凭证，有效凭证原样提交一次`)
}
console.log(`${passed} 项微信登录凭证行为验证通过`)

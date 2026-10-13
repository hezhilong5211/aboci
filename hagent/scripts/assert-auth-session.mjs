import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = (file) => readFileSync(path.join(root, file), 'utf8')
const executable = (text) => text.replace(/^import .*$/gm, '').replace(/^export default request\s*$/m, '').replace(/export /g, '')
const tick = () => new Promise(setImmediate)
let passed = 0
async function check(name, test) { await test(); passed++; console.log(`通过：${name}`) }
function harness(initial) {
	const storage = new Map(initial || [['access', 'old-access'], ['refresh', 'old-refresh'], ['user', { id: 1 }]])
	const calls = [], events = [], timers = [], navigations = [], toasts = []
	const uni = {
		getStorageSync: (key) => storage.get(key) ?? '',
		setStorageSync: (key, value) => storage.set(key, value),
		removeStorageSync: (key) => storage.delete(key),
		getStorageInfoSync: () => ({ keys: [...storage.keys()], currentSize: storage.size }),
		request: (options) => calls.push(options),
		showToast: (options) => toasts.push(options),
		$emit: (...args) => events.push(args),
	}
	const context = vm.createContext({ uni, console,
		config: { baseUrl: 'https://example.test', tokenKey: 'access', refreshTokenKey: 'refresh', userInfoKey: 'user', timeout: 15000 },
		getCurrentPages: () => [{ route: 'pages/index/index' }],
		navigateToLogin: (url) => navigations.push(url), setTimeout: (fn) => timers.push(fn),
	})
	vm.runInContext(executable(source('utils/storage.js')) + '\n' + executable(source('api/request.js')), context)
	const start = (options = {}) => {
		const promise = context.request({ url: '/protected', ...options })
		promise.catch(() => {})
		return promise
	}
	const respond = (index, data, statusCode = 200) => {
		calls[index].success({ statusCode, data }); calls[index].complete?.()
	}
	const refreshOK = (index) => respond(index, { code: 200, data: { token: 'new-access', refreshToken: 'new-refresh' } })
	return { storage, calls, events, timers, navigations, toasts, uni, context, start, respond, refreshOK }
}
for (const kind of ['timeout', 'http-502', 'http-429', 'business-429', 'business-500', 'malformed']) {
	await check(`${kind} 保留凭证且允许下次恢复`, async () => {
		const h = harness(), first = h.start()
		h.respond(0, { code: 401 })
		if (kind === 'timeout') h.calls[1].fail({ errMsg: 'request:fail timeout' })
		else if (kind.startsWith('http')) h.respond(1, 'gateway error', Number(kind.slice(5)))
		else h.respond(1, kind === 'malformed' ? { code: 200, data: {} } : { code: Number(kind.slice(9)), msg: '临时失败' })
		await assert.rejects(first, (e) => e.code === 'AUTH_REFRESH_UNAVAILABLE')
		assert.equal(h.storage.get('refresh'), 'old-refresh'); assert.equal(h.storage.get('access'), 'old-access')
		assert.equal(h.timers.length, 0)
		const second = h.start(); h.respond(2, { code: 401 }); h.refreshOK(3); await tick()
		assert.equal(h.calls[4].header.Authorization, 'Bearer new-access')
		h.respond(4, { code: 200, data: 'ok' }); assert.equal(await second, 'ok')
	})
}
await check('并发 401 共用一次续期并重试全部请求', async () => {
	const h = harness(), jobs = [h.start(), h.start(), h.start()]
	for (let i = 0; i < 3; i++) h.respond(i, { code: 401 })
	assert.equal(h.calls.length, 4); h.refreshOK(3); await tick()
	assert.equal(h.calls.length, 7)
	for (let i = 4; i < 7; i++) h.respond(i, { code: 200, data: 'ok' })
	assert.deepEqual(await Promise.all(jobs), ['ok', 'ok', 'ok'])
	assert.equal(h.events.filter(([name]) => name === 'auth-token-refreshed').length, 1)
})
await check('迟到的旧 401 使用新令牌、不再次续期', async () => {
	const h = harness(), jobs = [h.start(), h.start()]
	h.respond(0, { code: 401 }); h.refreshOK(2); await tick()
	h.respond(3, { code: 200 }); h.respond(1, { code: 401 }); await tick()
	assert.equal(h.calls[4].url, 'https://example.test/protected')
	assert.equal(h.calls[4].header.Authorization, 'Bearer new-access')
	h.respond(4, { code: 200 }); await Promise.all(jobs)
	assert.equal(h.calls.filter((c) => c.url.endsWith('/refreshToken')).length, 1)
})
await check('重试仍 401 时结束请求，不循环或清空有效凭证', async () => {
	const h = harness(), p = h.start(); h.respond(0, { code: 401 }); h.refreshOK(1); await tick()
	h.respond(2, { code: 401 }); await assert.rejects(p, (e) => e.code === 401)
	assert.equal(h.calls.length, 3); assert.equal(h.storage.get('refresh'), 'new-refresh')
})
await check('HTTP 401 与业务 401 使用相同续期流程', async () => {
	const h = harness(), p = h.start(); h.respond(0, {}, 401); h.refreshOK(1); await tick()
	h.respond(2, { code: 200, data: 'ok' }); assert.equal(await p, 'ok')
})
for (const kind of ['new-backend', 'legacy-backend', 'http-401']) {
	await check(`${kind} 明确失效才退出，并防止延迟跳转打断新登录`, async () => {
		const h = harness(), p = h.start(); h.respond(0, { code: 401 })
		h.respond(1, { code: kind === 'legacy-backend' ? 500 : 401, msg: '刷新令牌已失效，请重新登录' }, kind === 'http-401' ? 401 : 200)
		await assert.rejects(p, (e) => e.code === 401); assert.equal(h.storage.size, 0)
		assert.equal(h.timers.length, 1)
		h.storage.set('access', 'other-access'); h.context.setRefreshToken('other-refresh')
		h.timers[0](); assert.equal(h.navigations.length, 0)
	})
}
for (const response of ['success', 'invalid']) {
	await check(`旧会话续期 ${response} 不覆盖或清除新登录`, async () => {
		const h = harness(), p = h.start(); h.respond(0, { code: 401 })
		h.storage.set('access', 'account-b'); h.context.setRefreshToken('account-b-refresh')
		if (response === 'success') h.refreshOK(1); else h.respond(1, { code: 401 })
		await assert.rejects(p, (e) => e.code === 409)
		assert.equal(h.storage.get('access'), 'account-b'); assert.equal(h.storage.get('refresh'), 'account-b-refresh')
		assert.equal(h.calls.length, 2); assert.equal(h.timers.length, 0)
	})
}
await check('退出登录后迟到的续期结果不能恢复已退出会话', async () => {
	const h = harness(), p = h.start(); h.respond(0, { code: 401 })
	h.storage.delete('access'); h.context.setRefreshToken(null); h.refreshOK(1)
	await assert.rejects(p, (e) => e.code === 409); assert.equal(h.storage.has('access'), false)
})
await check('skipAuthRefresh 在 HTTP 401 下也不触发续期或跳转', async () => {
	const h = harness(), p = h.start({ skipAuthRefresh: true }); h.respond(0, { code: 401 }, 401)
	await assert.rejects(p); assert.equal(h.calls.length, 1); assert.equal(h.storage.size, 3)
})
await check('冷启动仍能读取持久化的刷新令牌', async () => {
	const h = harness([['access', 'persisted-access'], ['refresh', 'persisted-refresh']]), p = h.start()
	h.respond(0, { code: 401 }); assert.equal(h.calls[1].data.refreshToken, 'persisted-refresh')
	h.refreshOK(1); await tick(); h.respond(2, { code: 200 }); await p
})
await check('清缓存保留会话三项，删除失败也不丢登录凭证', async () => {
	const h = harness(); h.storage.set('cache-a', 1); h.storage.set('cache-b', 2)
	h.context.clearNonAuthStorage(); assert.equal(h.storage.size, 3)
	h.storage.set('cache-c', 3); h.uni.removeStorageSync = () => { throw Error('storage unavailable') }
	assert.throws(() => h.context.clearNonAuthStorage()); assert.equal(h.storage.get('refresh'), 'old-refresh')
})
await check('凭证写入失败保留可恢复的 refresh，不伪报登录成功', async () => {
	const h = harness(), p = h.start(); h.respond(0, { code: 401 })
	h.uni.setStorageSync = (key, value) => { if (key === 'access') throw Error('full'); h.storage.set(key, value) }
	h.refreshOK(1); await assert.rejects(p, (e) => e.code === 'AUTH_STORAGE_FAILED')
	assert.equal(h.storage.get('refresh'), 'new-refresh'); assert.equal(h.events.length, 0)
})
console.log(`${passed} 项登录会话行为验证通过`)

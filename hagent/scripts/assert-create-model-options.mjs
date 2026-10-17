import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, '../aurora-adui/package.json'))
const { ref, reactive, computed, proxyRefs, watch, effectScope, nextTick } = require('vue')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { parse: parseJavaScript } = require('@babel/parser')
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
const source = read('pages/create/CreateContent.vue')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1]
const ast = parseJavaScript(script, { sourceType: 'module' })
let executable = script
for (const node of ast.program.body.filter((item) => item.type === 'ImportDeclaration').reverse()) {
	executable = executable.slice(0, node.start) + executable.slice(node.end)
}
const models = [
	{ modelId: 11, displayTier: '轻', isDefault: true, supportsReferenceImages: true, maxReferenceImages: 9 },
	{ modelId: 22, displayTier: '高', supportsReferenceImages: true, maxReferenceImages: 9 },
]
const options = (modelId, beanCosts = { '1K': 6, '2K': 8, '4K': 10 }) => ({
	modelId, supportedResolutions: ['4K', '2K', '1K'], defaultResolution: '1K', beanCosts, aspectRatios: ['3:4', '1:1'],
})
const drain = async () => { await Promise.resolve(); await nextTick(); await Promise.resolve() }
function deferred() {
	let resolve, reject
	const promise = new Promise((yes, no) => { resolve = yes; reject = no })
	return { promise, resolve, reject }
}
function fixture() {
	const catalogueRequests = [], optionRequests = [], submissions = [], toasts = [], unmounts = []
	const scope = effectScope()
	const userStore = reactive({ token: 'account-a', userInfo: { id: 1 }, get isLoggedIn() { return !!this.token } })
	const context = vm.createContext({
		ref, reactive, computed, proxyRefs, watch,
		defineStore: (_, factory) => { let value; return () => (value ||= proxyRefs(factory())) },
		defineProps: () => ({ statusBarHeight: 44 }), defineEmits: () => () => {}, defineExpose() {},
		onMounted() {}, onUnmounted: (callback) => unmounts.push(callback),
		useUserStore: () => userStore, useAccountStore: () => ({ setBalance() {} }),
		usePerformanceMode: () => ({}), usePageScroll: () => ({}), useLoginGuard: () => ({ requireLogin: () => userStore.isLoggedIn }),
		uni: { getStorageSync() {}, setStorageSync() {}, hideKeyboard() {}, showToast: (value) => toasts.push(value), showModal() {} },
		getAvailableModels: () => { const request = deferred(); catalogueRequests.push(request); return request.promise },
		getGenerationOptions: (modelId) => { const request = { modelId, ...deferred() }; optionRequests.push(request); return request.promise },
		generateImage: async (body) => { submissions.push(structuredClone(body)); return { taskId: 'task-1' } },
		getGenerationConfig: async () => ({ generationEnabled: true }), getWorks: async () => ({ records: [] }),
		setInterval: () => 1, clearInterval() {}, setTimeout() {},
	})
	vm.runInContext(read('store/generation.js').replace(/^import .*$/gm, '').replace('export const useGenerationStore', 'var useGenerationStore'), context)
	vm.runInContext(read('utils/image-resolution.js').replace(/^export /gm, ''), context)
	scope.run(() => vm.runInContext(`${executable}\nglobalThis.binding = { generationStore, fetchModels, fetchGenerationOptions, selectImageModel, selectResolution, retryModelOptions, handleGenerate, imageModels, modelsReady, modelsLoading, modelsError, optionsLoading, optionsError, resolutionOptions, resolutionBeanCosts, modelOptionsReady, consumeTip, canSend }`, context))
	return { ...context.binding, catalogueRequests, optionRequests, submissions, toasts, userStore,
		dispose() { for (const callback of unmounts) callback(); scope.stop() },
	}
}
async function ready(f, modelId = 11) {
	f.generationStore.setModelId(modelId)
	const loading = f.fetchModels()
	f.catalogueRequests.at(-1).resolve({ imageModels: models })
	await drain()
	f.optionRequests.at(-1).resolve(options(modelId))
	await loading
}
let passed = 0
async function check(name, test) {
	const f = fixture()
	try { await test(f); console.log(`通过：${name}`); passed += 1 }
	finally { f.dispose() }
}

await check('模板高档 4K 9 张保留到请求，加载配置期间禁止提交', async (f) => {
	f.generationStore.setModelId(22)
	f.generationStore.setPrompt('模板提示词')
	f.generationStore.setParams({ resolution: '4K', n: 9 })
	const loading = f.fetchModels()
	f.catalogueRequests.at(-1).resolve({ imageModels: models })
	await drain()
	assert.equal(f.canSend.value, false)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 0)
	f.optionRequests.at(-1).resolve(options(22, { '1K': 15, '2K': 20, '4K': 30 }))
	await loading
	assert.equal(f.generationStore.params.resolution, '4K')
	assert.match(f.consumeTip.value, /270 豆/)
	await f.handleGenerate()
	assert.equal(f.submissions[0].modelId, 22)
	assert.equal(f.submissions[0].resolution, '4K')
	assert.equal(f.submissions[0].n, 9)
})

await check('A→B 切换同步清旧价格，迟到 A 失败不清除 B 配置', async (f) => {
	await ready(f)
	f.generationStore.setPrompt('测试')
	const oldA = f.fetchGenerationOptions()
	const stale = f.optionRequests.at(-1)
	f.selectImageModel(22)
	assert.equal(f.resolutionOptions.value.length, 0)
	assert.equal(Object.keys(f.resolutionBeanCosts.value).length, 0)
	assert.equal(f.canSend.value, false)
	f.optionRequests.at(-1).resolve(options(22, { '1K': 15 }))
	await drain()
	stale.reject(new Error('A timed out'))
	await oldA
	assert.equal(f.modelOptionsReady.value, true)
	assert.equal(f.resolutionBeanCosts.value['1K'], 15)
	assert.equal(f.optionsError.value, '')
})

await check('A→B→A 时第一轮 A 的迟到成功不能覆盖最新 A', async (f) => {
	await ready(f)
	const oldA = f.fetchGenerationOptions()
	const stale = f.optionRequests.at(-1)
	f.selectImageModel(22)
	const staleB = f.optionRequests.at(-1)
	f.selectImageModel(11)
	f.generationStore.setParams({ resolution: '4K' })
	f.optionRequests.at(-1).resolve(options(11, { '1K': 16, '2K': 18, '4K': 20 }))
	await drain()
	stale.resolve({ ...options(11), supportedResolutions: ['1K'] })
	staleB.reject(new Error('B timed out'))
	await oldA
	await drain()
	assert.equal(f.generationStore.params.resolution, '4K')
	assert.equal(f.resolutionBeanCosts.value['4K'], 20)
	assert.equal(f.optionsError.value, '')
})

await check('同模型重新带入模板时，响应保留新模板合法清晰度', async (f) => {
	await ready(f)
	const loading = f.fetchGenerationOptions()
	f.generationStore.setParams({ resolution: '4K', n: 7 })
	f.optionRequests.at(-1).resolve(options(11))
	await loading
	assert.equal(f.generationStore.params.resolution, '4K')
	assert.equal(f.generationStore.params.n, 7)
})

await check('模板档位已停用时不静默切换到默认档位、不发出图请求', async (f) => {
	f.generationStore.setModelId(99)
	f.generationStore.setPrompt('保留指定档位')
	const loading = f.fetchModels()
	f.catalogueRequests.at(-1).resolve({ imageModels: models })
	await loading
	assert.equal(f.generationStore.modelId, 99)
	assert.equal(f.optionRequests.length, 0)
	assert.equal(f.canSend.value, false)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 0)
	assert.match(f.consumeTip.value, /不可用/)
	f.selectImageModel(22)
	f.optionRequests.at(-1).resolve(options(22))
	await drain()
	assert.equal(f.modelOptionsReady.value, true)
})

await check('目录刷新失败撤销旧可提交状态，重试成功后恢复', async (f) => {
	await ready(f)
	f.generationStore.setPrompt('测试')
	const failed = f.fetchModels()
	f.catalogueRequests.at(-1).reject(new Error('offline'))
	await failed
	assert.equal(f.canSend.value, false)
	assert.equal(f.imageModels.value.length, 0)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 0)
	f.retryModelOptions()
	f.catalogueRequests.at(-1).resolve({ imageModels: models })
	await drain()
	f.optionRequests.at(-1).resolve(options(11))
	await drain()
	assert.equal(f.canSend.value, true)
})

await check('目录并发刷新时旧失败不覆盖新目录和选项', async (f) => {
	await ready(f)
	const old = f.fetchModels()
	const stale = f.catalogueRequests.at(-1)
	const latest = f.fetchModels()
	f.catalogueRequests.at(-1).resolve({ imageModels: models })
	await drain()
	f.optionRequests.at(-1).resolve(options(11))
	await latest
	stale.reject(new Error('older catalogue failed'))
	await old
	assert.equal(f.modelsReady.value, true)
	assert.equal(f.modelsError.value, '')
	assert.equal(f.modelOptionsReady.value, true)
})

await check('当前选项失败或返回另一档位时阻止提交，重试成功恢复', async (f) => {
	await ready(f)
	f.generationStore.setPrompt('测试')
	const wrongModel = f.fetchGenerationOptions()
	f.optionRequests.at(-1).resolve(options(22))
	await wrongModel
	assert.equal(f.canSend.value, false)
	assert.match(f.optionsError.value, /加载失败/)
	f.retryModelOptions()
	f.optionRequests.at(-1).resolve(options(11))
	await drain()
	assert.equal(f.canSend.value, true)
})

await check('空价格与未配置清晰度都禁止提交，并显示可重试提示', async (f) => {
	await ready(f)
	f.generationStore.setPrompt('测试')
	f.generationStore.setParams({ resolution: '4K' })
	const missingPrice = f.fetchGenerationOptions()
	f.optionRequests.at(-1).resolve(options(11, { '1K': '', '2K': null, '4K': null }))
	await missingPrice
	assert.equal(Object.keys(f.resolutionBeanCosts.value).length, 0)
	assert.doesNotMatch(f.consumeTip.value, /消耗 0 豆/)
	assert.match(f.consumeTip.value, /价格未配置，点击重试/)
	assert.equal(f.canSend.value, false)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 0)
	const unsupported = f.fetchGenerationOptions()
	f.optionRequests.at(-1).resolve({ ...options(11), supportedResolutions: [] })
	await unsupported
	assert.equal(f.generationStore.params.resolution, '')
	assert.equal(f.canSend.value, false)
	assert.match(f.consumeTip.value, /未配置可用清晰度，点击重试/)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 0)
})

await check('缺价清晰度可改选已配置清晰度，合法零豆仍可提交', async (f) => {
	await ready(f)
	f.generationStore.setPrompt('测试')
	f.generationStore.setParams({ resolution: '2K' })
	const loading = f.fetchGenerationOptions()
	f.optionRequests.at(-1).resolve(options(11, { '1K': 0, '2K': null, '4K': 10 }))
	await loading
	assert.equal(f.canSend.value, false)
	f.selectResolution('4K')
	assert.equal(f.canSend.value, true)
	assert.match(f.consumeTip.value, /预计消耗 10 豆/)
	f.selectResolution('1K')
	assert.equal(f.canSend.value, true)
	assert.match(f.consumeTip.value, /预计消耗 0 豆/)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 1)
	assert.equal(f.submissions[0].resolution, '1K')
})

await check('缺失 supportedResolutions 字段时不提交，重试完整配置后恢复', async (f) => {
	await ready(f)
	f.generationStore.setPrompt('测试')
	const loading = f.fetchGenerationOptions()
	f.optionRequests.at(-1).resolve({ ...options(11), supportedResolutions: undefined })
	await loading
	assert.equal(f.canSend.value, false)
	await f.handleGenerate()
	assert.equal(f.submissions.length, 0)
	f.retryModelOptions()
	f.optionRequests.at(-1).resolve(options(11))
	await drain()
	assert.equal(f.canSend.value, true)
})

await check('退出登录后迟到选项响应不恢复旧账号配置', async (f) => {
	await ready(f)
	const pending = f.fetchGenerationOptions()
	const stale = f.optionRequests.at(-1)
	f.userStore.token = ''
	stale.resolve(options(11))
	await pending
	assert.equal(f.modelsReady.value, false)
	assert.equal(f.modelOptionsReady.value, false)
	assert.equal(f.resolutionOptions.value.length, 0)
})

const { descriptor, errors } = parse(source)
assert.deepEqual(errors, [])
compileScript(descriptor, { id: 'create-options-regression' })
assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'CreateContent.vue', id: 'create-options-regression' }).errors, [])
console.log(`生成页档位与选项回归通过，共 ${passed} 组。`)

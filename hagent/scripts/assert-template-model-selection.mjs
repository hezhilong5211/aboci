import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, '../aurora-adui/package.json'))
const { ref, reactive, computed, watch, proxyRefs, effectScope, nextTick, unref } = require('vue')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { parse: parseJavaScript } = require('@babel/parser')
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
const filename = 'pages/cases/components/UseTemplateModal.vue'
const source = read(filename)
const { descriptor, errors } = parse(source, { filename })
assert.deepEqual(errors, [])

// Run the actual component setup and generation store with Vue reactivity. Only I/O is replaced;
// deferred requests let these checks reproduce close/reopen and duplicate-confirm races.
function executable(source) {
	const ast = parseJavaScript(source, { sourceType: 'module' })
	const removals = []
	for (const item of ast.program.body) {
		if (item.type === 'ImportDeclaration') removals.push([item.start, item.end])
		else if (item.type === 'ExportNamedDeclaration' && item.declaration) removals.push([item.start, item.declaration.start])
	}
	for (const [start, end] of removals.reverse()) source = source.slice(0, start) + source.slice(end)
	return source
}
const plain = (value) => JSON.parse(JSON.stringify(value))
const flush = async () => { for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() } }
function deferred() {
	let resolve, reject
	const promise = new Promise((yes, no) => { resolve = yes; reject = no })
	return { promise, resolve, reject }
}
const catalog = () => [
	{ modelId: 11, modelType: 'image', displayTier: '轻', isDefault: false,
		resolutions: ['1K', '2K', '4K'], defaultResolution: '1K', aspectRatios: ['3:4', '1:1'],
		beanCosts: { '1K': 6, '2K': 8, '4K': 10 }, supportsReferenceImages: true, maxReferenceImages: 9 },
	{ modelId: 22, modelType: 'image', displayTier: '中', isDefault: true,
		resolutions: ['1K', '2K', '4K'], defaultResolution: '1K', aspectRatios: ['3:4', '1:1'],
		beanCosts: { '1K': 10, '2K': 15, '4K': 20 }, supportsReferenceImages: true, maxReferenceImages: 9 },
	{ modelId: 33, modelType: 'image', displayTier: '高', isDefault: false,
		resolutions: ['1K', '4K'], defaultResolution: '4K', aspectRatios: ['1:1'],
		beanCosts: { '1K': 15, '4K': 30 }, supportsReferenceImages: false, maxReferenceImages: 0 },
]
const template = (params = {}, extra = {}) => ({ templateId: 101, prompt: '保持发型和服装，生成独立图片', params: JSON.stringify(params), ...extra })

function harness({ loggedIn = true, initialModel = null, initialResolution = '' } = {}) {
	const props = reactive({ visible: false, template: {} })
	const user = reactive({ isLoggedIn: loggedIn })
	const catalogRequests = [], useRequests = [], applications = [], switches = [], emissions = [], toasts = []
	const unmounts = [], storage = new Map()
	let loginPrompts = 0
	const uni = { getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, plain(value)),
		showToast: ({ title }) => toasts.push(title) }
	const storeContext = vm.createContext({ ref, uni, defineStore: (_, setup) => () => proxyRefs(setup()) })
	vm.runInContext(executable(read('store/generation.js')), storeContext)
	const store = vm.runInContext('useGenerationStore()', storeContext)
	store.setModelId(initialModel)
	store.setParams({ resolution: initialResolution })
	const apply = store.applyTemplate
	store.applyTemplate = (payload) => { applications.push(plain(payload)); apply(payload) }
	const scope = effectScope()
	const context = vm.createContext({ ref, reactive, computed, watch, uni,
		defineProps: () => props,
		defineEmits: () => (event, value) => {
			emissions.push([event, value])
			if (event === 'update:visible') props.visible = value
		},
		onUnmounted: (fn) => unmounts.push(fn),
		useGenerationStore: () => store,
		useTabbarStore: () => ({ switchTo: (tab) => switches.push(tab) }),
		useUserStore: () => user,
		useLoginGuard: () => ({ requireLogin: () => { if (!user.isLoggedIn) loginPrompts++; return user.isLoggedIn } }),
		getAvailableModels: () => { const request = deferred(); catalogRequests.push(request); return request.promise },
		useTemplate: (templateId) => { const request = { ...deferred(), templateId }; useRequests.push(request); return request.promise },
	})
	vm.runInContext(executable(read('utils/image-resolution.js')), context)
	scope.run(() => vm.runInContext(executable(descriptor.scriptSetup.content), context))
	return {
		props, user, store, catalogRequests, useRequests, applications, switches, emissions, toasts,
		get loginPrompts() { return loginPrompts },
		value: (name) => unref(vm.runInContext(name, context)),
		set(name, value) { vm.runInContext(name, context).value = value },
		call(name, ...args) { return vm.runInContext(name, context)(...args) },
		async open(value = template()) { props.template = value; props.visible = true; await flush() },
		async resolveCatalog(index = catalogRequests.length - 1, models = catalog()) { catalogRequests[index].resolve({ imageModels: plain(models) }); await flush() },
		async ready(value = template(), models = catalog()) { await this.open(value); await this.resolveCatalog(catalogRequests.length - 1, models) },
		async close() { this.call('onClose'); await flush() },
		dispose() { unmounts.forEach((fn) => fn()); scope.stop() },
	}
}

let passed = 0
async function check(name, test, options) {
	const h = harness(options)
	try { await test(h); console.log(`通过：${name}`); passed++ } finally { h.dispose() }
}
const noApplication = (h) => {
	assert.equal(h.applications.length, 0)
	assert.equal(h.switches.length, 0)
}

await check('打开弹窗选择后台默认档位，张数1～9，按所选清晰度计算九张费用', async (h) => {
	await h.ready(template({ count: 9 }))
	assert.equal(h.value('selectedModelId'), 22)
	assert.equal(h.value('resolution'), '1K')
	assert.deepEqual(plain(h.value('countOptions')), [1, 2, 3, 4, 5, 6, 7, 8, 9])
	assert.equal(h.value('consumeTip'), '本次生成 9 张 · 预计消耗 90 豆')
	for (const [input, expected] of [[99, 9], [0, 1], [-1, 1], ['9', 9], [2.5, 1], ['bad', 1], [null, 1]]) {
		assert.equal(h.call('normalizeCount', input), expected)
	}
	noApplication(h)
})

await check('模板显式档位优先于生成页旧选择，合法4K与模板张数完整带入', async (h) => {
	await h.ready(template({ modelId: 22, resolution: '4k', n: 8 }))
	assert.equal(h.value('selectedModelId'), 22)
	assert.equal(h.value('resolution'), '4K')
	assert.equal(h.value('count'), 8)
	assert.equal(h.value('consumeTip'), '本次生成 8 张 · 预计消耗 160 豆')
	assert.equal(h.store.modelId, 11, 'editing the modal must not change the generation draft')
}, { initialModel: 11, initialResolution: '2K' })

await check('模板未指定档位时保留生成页合法选择，失效模板ID可见回退后台默认', async (h) => {
	await h.ready(template())
	assert.equal(h.value('selectedModelId'), 11)
	assert.equal(h.value('resolution'), '4K')
	await h.close()
	await h.ready(template({ modelId: 999, resolution: '8K', count: 100 }))
	assert.equal(h.value('selectedModelId'), 22)
	assert.equal(h.value('resolution'), '1K')
	assert.equal(h.value('count'), 9)
	const confirmation = h.call('onConfirm')
	h.useRequests[0].resolve()
	await confirmation
	assert.equal(h.applications[0].modelId, 22)
	assert.equal(h.store.modelId, 22)
}, { initialModel: 11, initialResolution: '4K' })

await check('切换档位保留兼容清晰度，非法清晰度与画幅回退并更新费用', async (h) => {
	await h.ready(template({ resolution: '2K', count: 3 }))
	h.call('selectImageModel', 11)
	assert.equal(h.value('resolution'), '2K')
	assert.equal(h.value('consumeTip'), '本次生成 3 张 · 预计消耗 24 豆')
	h.call('selectImageModel', 33)
	assert.equal(h.value('resolution'), '4K')
	assert.equal(h.value('aspectRatio'), '1:1')
	assert.equal(h.value('consumeTip'), '本次生成 3 张 · 预计消耗 90 豆')
	h.call('selectImageModel', 999)
	assert.equal(h.value('selectedModelId'), 33)
	h.set('selectedModelId', 999)
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0, 'an ID missing from the current catalogue must not be applied')
	noApplication(h)
})

await check('加载中、加载失败与空目录不能确认，重试成功后才可选择', async (h) => {
	await h.open()
	assert.equal(h.value('modelsLoading'), true)
	await h.call('onConfirm')
	h.catalogRequests[0].reject(new Error('offline'))
	await flush()
	assert.equal(h.value('modelsLoading'), false)
	assert.ok(h.value('modelError'))
	await h.call('onConfirm')
	h.call('retryModels')
	await h.resolveCatalog(1, [])
	assert.equal(h.value('selectedModelId'), null)
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0)
	h.call('retryModels')
	await h.resolveCatalog(2)
	assert.equal(h.value('selectedModelId'), 22)
	assert.equal(h.value('modelError'), '')
	noApplication(h)
})

await check('未支持的清晰度或缺失价格不能误发，零豆配置不被误认为缺失', async (h) => {
	const models = catalog()
	models[1].beanCosts['2K'] = null
	await h.ready(template({ resolution: '2K' }), models)
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0)
	h.set('resolution', '8K')
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0)
	await h.close()
	models[1].beanCosts['1K'] = 0
	await h.ready(template({ resolution: '1K' }), models)
	assert.equal(h.value('consumeTip'), '本次生成 1 张 · 预计消耗 0 豆')
	const confirmation = h.call('onConfirm')
	h.useRequests[0].resolve()
	await confirmation
	assert.equal(h.applications.length, 1)
})

await check('图生图阻止无参考图、不支持参考图档位及超出档位参考图数量', async (h) => {
	const models = catalog()
	models[1].maxReferenceImages = 2
	await h.ready(template({ mode: 'img2img' }), models)
	await h.call('onConfirm')
	h.set('refImages', ['/profile/a.png', '/profile/b.png', '/profile/c.png'])
	await h.call('onConfirm')
	h.call('selectImageModel', 33)
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0)
	assert.ok(h.toasts.some((text) => text.includes('请先上传')))
	assert.ok(h.toasts.some((text) => text.includes('最多支持 2')))
	assert.ok(h.toasts.some((text) => text.includes('不支持参考图')))
	noApplication(h)
})

await check('确认把modelId、resolution、九张数量及有序参考图写入真实生成store，重复确认仅应用一次', async (h) => {
	await h.ready(template({ mode: 'img2img', modelId: 22, count: 9, resolution: '2K' }, { referenceImages: '["/profile/a.png","/profile/b.png"]' }))
	h.call('selectImageModel', 11)
	h.call('reorderRefs', [1, 0])
	const confirmation = h.call('onConfirm')
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 1)
	assert.equal(h.value('confirming'), true)
	h.call('selectImageModel', 22)
	assert.equal(h.value('selectedModelId'), 11)
	h.set('promptText', '晚到的编辑不应污染已经确认的快照')
	h.set('resolution', '4K')
	h.value('refImages').push('/profile/late.png')
	h.useRequests[0].resolve()
	await confirmation
	assert.equal(h.applications.length, 1)
	assert.deepEqual(h.applications[0], { prompt: '保持发型和服装，生成独立图片', mode: 'img2img', modelId: 11,
		resolution: '2K', n: 9, aspectRatio: '3:4', refImages: ['/profile/b.png', '/profile/a.png'] })
	assert.equal(h.store.modelId, 11)
	assert.equal(h.store.params.resolution, '2K')
	assert.equal(h.store.params.n, 9)
	assert.deepEqual(plain(h.store.refImages), ['/profile/b.png', '/profile/a.png'])
	assert.equal(h.store.broughtIn, true)
	assert.deepEqual(h.switches, ['create'])
	assert.equal(h.emissions.filter(([event]) => event === 'confirm').length, 1)
	assert.equal(h.props.visible, false)
}, { initialModel: 33, initialResolution: '4K' })

await check('使用次数统计失败仍带入一次，文生图清空旧参考图', async (h) => {
	h.store.applyTemplate({ mode: 'img2img', refImages: ['/profile/stale.png'] })
	h.applications.length = 0
	await h.ready(template({ mode: 'text2img' }, { referenceImages: '["/profile/template.png"]' }))
	const confirmation = h.call('onConfirm')
	h.useRequests[0].reject(new Error('statistics unavailable'))
	await confirmation
	assert.equal(h.applications.length, 1)
	assert.deepEqual(plain(h.store.refImages), [])
	assert.equal(h.store.mode, 'text2img')
	assert.deepEqual(h.switches, ['create'])
})

await check('关闭重开后旧目录成功或失败均不能覆盖新弹窗，也不能提前解除加载状态', async (h) => {
	await h.open(template({ modelId: 11 }))
	await h.close()
	await h.open(template({ modelId: 33, resolution: '4K' }, { templateId: 102 }))
	await h.resolveCatalog(0, catalog().slice(0, 1))
	assert.equal(h.value('modelsLoading'), true)
	assert.equal(h.value('selectedModelId'), 33)
	assert.equal(h.value('imageModels').length, 0)
	await h.resolveCatalog(1)
	assert.equal(h.value('selectedModelId'), 33)
	await h.close()
	await h.open(template({ modelId: 11 }))
	await h.close()
	await h.open(template({ modelId: 22 }))
	h.catalogRequests[2].reject(new Error('old failure'))
	await flush()
	assert.equal(h.value('modelsLoading'), true)
	assert.equal(h.value('modelError'), '')
	await h.resolveCatalog(3)
	assert.equal(h.value('selectedModelId'), 22)
	noApplication(h)
})

await check('旧确认在关闭重开后完成不能应用旧模板、关闭新弹窗或解除新确认锁', async (h) => {
	await h.ready(template({ modelId: 11 }))
	const oldConfirmation = h.call('onConfirm')
	await h.close()
	await h.ready(template({ modelId: 22, count: 2 }, { templateId: 202, prompt: '新模板需求' }))
	const newConfirmation = h.call('onConfirm')
	assert.deepEqual(h.useRequests.map((request) => request.templateId), [101, 202])
	h.useRequests[0].resolve()
	await oldConfirmation
	noApplication(h)
	assert.equal(h.props.visible, true)
	assert.equal(h.value('confirming'), true)
	h.useRequests[1].resolve()
	await newConfirmation
	assert.equal(h.applications.length, 1)
	assert.equal(h.store.prompt, '新模板需求')
	assert.equal(h.store.modelId, 22)
	assert.equal(h.store.params.n, 2)
	assert.deepEqual(h.switches, ['create'])
})

await check('同一弹窗更换模板使旧确认失效，上传中及空提示词不触发统计或带入', async (h) => {
	await h.ready()
	h.set('uploading', true)
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0)
	h.set('uploading', false)
	h.set('promptText', '   ')
	await h.call('onConfirm')
	assert.equal(h.useRequests.length, 0)
	h.set('promptText', '第一份需求')
	const oldConfirmation = h.call('onConfirm')
	await h.open(template({ count: 4 }, { templateId: 404, prompt: '切换后的需求' }))
	h.useRequests[0].resolve()
	await oldConfirmation
	noApplication(h)
	assert.equal(h.props.visible, true)
	assert.equal(h.value('promptText'), '切换后的需求')
	assert.equal(h.value('count'), 4)
	await h.resolveCatalog()
})

await check('未登录不读目录也不确认，登录后加载；确认期间退出登录不带入', async (h) => {
	await h.open()
	assert.equal(h.catalogRequests.length, 0)
	await h.call('onConfirm')
	assert.equal(h.loginPrompts, 1)
	h.user.isLoggedIn = true
	await flush()
	await h.resolveCatalog()
	const confirmation = h.call('onConfirm')
	h.user.isLoggedIn = false
	await flush()
	h.useRequests[0].resolve()
	await confirmation
	noApplication(h)
	assert.equal(h.value('imageModels').length, 0)
}, { loggedIn: false })

await check('卸载后未完成目录与确认请求均不得应用或切页', async (h) => {
	await h.ready()
	const confirmation = h.call('onConfirm')
	const reload = h.call('loadModels')
	h.dispose()
	h.catalogRequests.at(-1).resolve({ imageModels: catalog().slice(0, 1) })
	h.useRequests[0].resolve()
	await Promise.all([confirmation, reload])
	noApplication(h)
	assert.equal(h.value('imageModels').length, 0)
})

const compiled = compileScript(descriptor, { id: 'template-model-selection' })
const templateCompilation = compileTemplate({ source: descriptor.template.content, filename,
	id: 'template-model-selection', compilerOptions: { bindingMetadata: compiled.bindings } })
assert.deepEqual(templateCompilation.errors, [])
console.log(`模板模型选择回归通过：${passed} 组行为检查及 Vue 模板编译；未请求生产服务。`)

import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const [chat, uploader, create, template] = await Promise.all([
	'pages/index/ChatContent.vue',
	'pages/create/components/RefImageUploader.vue',
	'pages/create/CreateContent.vue',
	'pages/cases/components/UseTemplateModal.vue',
].map((file) => readFile(path.join(mobileRoot, file), 'utf8')))

// 执行组件实际事件函数，以可控上传 Promise 验证顺序和切换输入时的竞态。
function install(source, names, bindings) {
	const context = vm.createContext(bindings)
	for (const name of names) {
		const match = source.match(new RegExp(`(?:async )?function ${name}\\([^]*?\\n}`))
		assert.ok(match, `找不到函数 ${name}`)
		vm.runInContext(match[0], context)
	}
	return context
}

const ref = (value) => ({ value })
const plain = (value) => JSON.parse(JSON.stringify(value))
const noop = () => {}
const tick = async () => { await Promise.resolve(); await Promise.resolve() }

function uploadHarness() {
	const selections = []
	const uploads = []
	const toasts = []
	return {
		selections, uploads, toasts,
		uni: { chooseImage: (options) => selections.push(options), showToast: (options) => toasts.push(options.title) },
		uploadFile: (filePath) => new Promise((resolve, reject) => uploads.push({ filePath, resolve, reject })),
	}
}

function chatHarness() {
	const io = uploadHarness()
	const requests = []
	const store = { conversationId: 42, currentSession: {}, messages: [], setActiveMessageId: noop, setStreaming: noop }
	store.addMessage = (message) => store.messages.push(message)
	const bindings = {
		...io, requests, streams: new Map(),
		pendingImages: ref([]), referenceUploading: ref(false), referenceUploadVersion: 0,
		interactionLocked: ref(false), hasGeneratingImage: ref(false), inputText: ref('生成图片'),
		pendingClarification: ref(null), selectedClarificationOptionId: ref(''), activeSkill: ref(''),
		orderEditorVisible: ref(false), conversationStore: store,
		requireLogin: () => true, createRequestId: () => 'request-1',
		ensureModelSelection: noop, measureUserMessages: noop, hideKeyboardIfFocused: noop, scrollToBottom: noop,
		createStreamHandlers: () => ({}), chatStream: (payload) => requests.push(payload),
	}
	bindings.setInputText = (text) => { bindings.inputText.value = text }
	return install(chat, [
		'chooseAndUpload', 'isReferenceUploadPending', 'cancelReferenceUpload', 'saveInputDraft', 'restoreInputDraft',
		'addGeneratedReference', 'removeImage', 'applyPendingImageOrder', 'handleSend',
	], bindings)
}

// 选择器打开时也必须占住本批次，取消后才能重新选择。
{
	const c = chatHarness()
	c.chooseAndUpload()
	c.chooseAndUpload()
	assert.equal(c.selections.length, 1)
	assert.deepEqual(plain(c.selections[0].sizeType), ['original'])
	assert.equal(c.referenceUploading.value, true)
	c.selections[0].fail()
	assert.equal(c.referenceUploading.value, false)
	c.chooseAndUpload()
	assert.equal(c.selections.length, 2)
}

// 慢上传不会让发送漏图，也不会让后加生成图插到未完成批次前面。
{
	const c = chatHarness()
	c.pendingImages.value = [{ type: 'upload', url: '/profile/existing' }]
	c.chooseAndUpload()
	const done = c.selections[0].success({ tempFilePaths: ['A', 'B'] })
	assert.deepEqual(c.uploads.map((upload) => upload.filePath), ['A'])
	await c.handleSend()
	c.addGeneratedReference({ taskId: 'old-task', seq: 1, url: '/profile/generated' })
	assert.equal(c.requests.length, 0)
	assert.equal(c.pendingImages.value.length, 1)
	c.uploads[0].resolve({ url: '/profile/A' })
	await tick()
	assert.deepEqual(c.uploads.map((upload) => upload.filePath), ['A', 'B'])
	assert.equal(c.referenceUploading.value, true)
	c.uploads[1].resolve({ url: '/profile/B' })
	await done
	assert.deepEqual(plain(c.pendingImages.value).map((image) => image.url), ['/profile/existing', '/profile/A', '/profile/B'])
	assert.equal(c.referenceUploading.value, false)
}

// 切会话后旧请求返回不能串图，也不能解锁新会话正在上传的批次。
{
	const c = chatHarness()
	c.chooseAndUpload()
	const oldDone = c.selections[0].success({ tempFilePaths: ['old-A', 'old-B'] })
	c.saveInputDraft()
	c.conversationStore.currentSession = {}
	c.restoreInputDraft()
	c.chooseAndUpload()
	const newDone = c.selections[1].success({ tempFilePaths: ['new-A'] })
	c.uploads[0].resolve({ url: '/profile/old-A' })
	await oldDone
	assert.equal(c.referenceUploading.value, true)
	assert.equal(c.pendingImages.value.length, 0)
	assert.deepEqual(c.uploads.map((upload) => upload.filePath), ['old-A', 'new-A'])
	c.uploads[1].resolve({ url: '/profile/new-A' })
	await newDone
	assert.deepEqual(plain(c.pendingImages.value), [{ type: 'upload', url: '/profile/new-A' }])
}

// 上传图与生成图混排、删除后，消息显示和实际 imageInputs 使用同一顺序。
{
	const c = chatHarness()
	c.pendingImages.value = [
		{ type: 'upload', url: '/profile/A' },
		{ type: 'upload', url: '/profile/B' },
		{ type: 'generated', taskId: 'task-G', seq: 2, url: '/profile/G' },
	]
	c.applyPendingImageOrder([2, 0, 1])
	c.removeImage(1)
	c.pendingClarification.value = { scopeId: 'scope-1' }
	c.selectedClarificationOptionId.value = 'option-1'
	c.conversationStore.currentSession.imageResolution = '4K'
	await c.handleSend()
	assert.equal(c.requests[0].imageResolution, '4K')
	assert.deepEqual(plain(c.requests[0].imageInputs), [
		{ type: 'generated', taskId: 'task-G', seq: 2 },
		{ type: 'upload', url: '/profile/B' },
	])
	assert.deepEqual(plain(c.conversationStore.messages[0].images).map((image) => image.url), ['/profile/G', '/profile/B'])
	assert.equal(c.requests[0].clarificationScopeId, 'scope-1')
	assert.equal(c.requests[0].clarificationOptionId, 'option-1')
	assert.equal(c.pendingImages.value.length, 0)
}

// 无新附件的澄清回复只回传 scope，不从历史中重排或重复提交原附件。
{
	const c = chatHarness()
	c.pendingClarification.value = { scopeId: 'scope-original' }
	await c.handleSend()
	assert.equal(c.requests[0].imageInputs, undefined)
	assert.equal(c.requests[0].clarificationScopeId, 'scope-original')
}

function createHarness() {
	const io = uploadHarness()
	const added = []
	const referenceUploading = ref(false)
	const events = []
	const bindings = {
		...io, added, events,
		props: { max: 9, images: [] }, uploading: ref(false), uploadVersion: 0,
		referenceUploading,
		emit: (name, value) => {
			events.push([name, value])
			if (name === 'add') added.push(value)
			if (name === 'uploading-change') referenceUploading.value = value
		},
	}
	return install(uploader, ['choose', 'setUploading', 'cancelUpload'], bindings)
}

// 两处均保留原图，超限时整批拒绝，不悄悄压缩或跳过导致图号前移。
for (const kind of ['chat', 'create']) {
	const c = kind === 'chat' ? chatHarness() : createHarness()
	if (kind === 'chat') c.chooseAndUpload()
	else c.choose()
	assert.deepEqual(plain(c.selections[0].sizeType), ['original'])
	await c.selections[0].success({ tempFilePaths: ['A', 'too-large'], tempFiles: [{ size: 1024 }, { size: 10 * 1024 * 1024 + 1 }] })
	assert.equal(c.uploads.length, 0)
	assert.match(c.toasts[0], /10MB/)
	assert.equal(c.referenceUploading.value, false)
	if (kind === 'chat') c.chooseAndUpload()
	else c.choose()
	await c.selections[1].success({ tempFilePaths: ['A', 'B', 'C', 'D', 'E', 'F'], tempFiles: Array.from({ length: 6 }, () => ({ size: 9 * 1024 * 1024 })) })
	assert.equal(c.uploads.length, 0)
	assert.match(c.toasts.at(-1), /合计不能超过50MB/)
	assert.equal(c.referenceUploading.value, false)
}

// 生成页同样整批锁定；一张失败不追加部分批次，用户重试后仍保持原编号。
{
	const c = createHarness()
	c.choose()
	c.choose()
	assert.equal(c.selections.length, 1)
	const done = c.selections[0].success({ tempFilePaths: ['A', 'failed-B', 'C'] })
	c.uploads[0].resolve({ url: '/profile/A' })
	await tick()
	assert.deepEqual(c.added, [])
	assert.equal(c.referenceUploading.value, true)
	const sender = install(create, ['handleGenerate'], {
		referenceUploading: c.referenceUploading, uni: c.uni,
		canSend: { get value() { throw new Error('上传结束前不应进入提交逻辑') } },
	})
	await sender.handleGenerate()
	c.uploads[1].reject(new Error('mock upload failure'))
	await done
	assert.deepEqual(c.added, [])
	assert.equal(c.uploads.length, 2)
	assert.match(c.toasts.at(-1), /第2张上传失败/)
	assert.equal(c.referenceUploading.value, false)
	c.choose()
	const retry = c.selections[1].success({ tempFilePaths: ['A', 'B'] })
	c.uploads[2].resolve({ url: '/profile/A' }); await tick()
	assert.deepEqual(c.added, [])
	c.uploads[3].resolve({ url: '/profile/B' }); await retry
	assert.deepEqual(c.added, ['/profile/A', '/profile/B'])
}

// 对话页上传失败不动已有参考图，后面的图片也不会占用失败图片的编号。
{
	const c = chatHarness()
	c.pendingImages.value = [{ type: 'upload', url: '/existing' }]
	c.chooseAndUpload()
	const done = c.selections[0].success({ tempFilePaths: ['A', 'B', 'C'] })
	c.uploads[0].resolve({ url: '/profile/A' }); await tick()
	c.uploads[1].reject(new Error('failed')); await done
	assert.deepEqual(plain(c.pendingImages.value).map((item) => item.url), ['/existing'])
	assert.equal(c.uploads.length, 2)
	assert.match(c.toasts.at(-1), /第2张上传失败/)
	assert.equal(c.referenceUploading.value, false)
}

// 模板替换/组件卸载后，旧上传不能污染新作品输入。
{
	const c = createHarness()
	c.choose()
	const done = c.selections[0].success({ tempFilePaths: ['old-image'] })
	c.cancelUpload()
	c.uploads[0].resolve({ url: '/profile/old-image' })
	await done
	assert.deepEqual(c.added, [])
	assert.equal(c.referenceUploading.value, false)
	assert.match(uploader, /watch\(\(\) => props\.images, cancelUpload, \{ flush: 'sync' \}\)/)
	assert.match(uploader, /onBeforeUnmount\(cancelUpload\)/)
	assert.match(create, /@uploading-change="referenceUploading = \$event"/)
	assert.match(chat, /onBeforeUnmount\(\(\) => \{[\s\S]*?cancelReferenceUpload\(\)/)
}

// 历史依赖作品仍可展示，但编辑不能把中途成品自动带回新输入。
{
	const source = (await readFile(path.join(mobileRoot, 'store/generation.js'), 'utf8')).replace(/^\t/gm, '')
	const c = install(source, ['restoreWorkItem'], {
		prompt: ref('当前草稿'), mode: ref('text2img'), params: ref({ n: 2 }), refImages: ref(['/profile/current']),
	})
	assert.equal(c.restoreWorkItem({ replaySupported: false, prompt: '旧任务', referenceImages: ['/profile/generated/old'] }), false)
	assert.equal(c.prompt.value, '当前草稿')
	assert.deepEqual(plain(c.refImages.value), ['/profile/current'])
	assert.equal(c.restoreWorkItem({ replaySupported: true, prompt: '合法单张', mode: 'img2img', referenceImages: ['/profile/B', '/profile/A'] }), true)
	assert.deepEqual(plain(c.refImages.value), ['/profile/B', '/profile/A'])
	const io = uploadHarness()
	const ui = install(create, ['editWorkItem'], {
		uni: io.uni, generationStore: { restoreWorkItem: () => false },
		mode: ref('text2img'), closeWorkDetail: () => { throw new Error('不应关闭历史详情') },
	})
	ui.editWorkItem({ replaySupported: false, replayUnavailableReason: '请重新选择素材' })
	assert.deepEqual(io.toasts, ['请重新选择素材'])
}
// 模板入口复用同一上传组件，上传未完成时不能把缺少参考图的输入带入生成页。
{
	assert.match(template, /<RefImageUploader[\s\S]*?:max="4"[\s\S]*?@uploading-change="uploading = \$event"/)
	assert.doesNotMatch(template, /sizeType|\bchooseImage\s*\(/)
	const toasts = []
	const c = install(template, ['onConfirm', 'reorderRefs'], {
		uploading: ref(true), uni: { showToast: ({ title }) => toasts.push(title) },
		refImages: ref(['/A', '/B']),
	})
	await c.onConfirm()
	assert.deepEqual(toasts, ['参考图上传中，请稍候'])
	c.reorderRefs([1, 0]); assert.deepEqual(plain(c.refImages.value), ['/B', '/A'])
	c.reorderRefs([0, 0]); assert.deepEqual(plain(c.refImages.value), ['/B', '/A'])
}
console.log('参考图顺序与上传绑定回归通过：12组（含原图限制、整批上传、模板复用和旧依赖作品恢复拦截）')

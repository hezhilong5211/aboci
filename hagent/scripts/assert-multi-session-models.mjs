import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, '../aurora-adui/package.json'))
const { ref, reactive, computed, proxyRefs } = require('vue')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { parse: parseJavaScript } = require('@babel/parser')
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
const storage = new Map([['user', { id: 1 }]])
const uni = { getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, structuredClone(value)), showToast() {}, showModal() {} }
const storeContext = vm.createContext({ ref, reactive, computed, uni, config: { userInfoKey: 'user' }, defineStore: (_, factory) => () => proxyRefs(factory()) })
vm.runInContext(read('store/conversation.js').replace(/^import .*$/gm, '').replace('export const useConversationStore', 'var useConversationStore'), storeContext)
const store = storeContext.useConversationStore()
let passed = 0
async function check(name, test) { await test(); console.log(`通过：${name}`); passed++ }
await check('切换与新建保留各会话消息、执行状态及模型选择', () => {
	store.setModels(11, 21); store.setConversationId(1)
	store.addMessage({ role: 'user', content: 'A' }); store.setStreaming(true); store.setActiveMessageId('A-run')
	store.reset(); store.setModels(12, 22); store.setConversationId(2); store.addMessage({ content: 'B' })
	assert.equal(store.getSession(1).messages[0].content, 'A'); assert.equal(store.getSession(1).streaming, true)
	store.setConversationId(1); assert.equal(store.currentSession.modelId, 11); assert.equal(store.currentSession.imageModelId, 21)
	assert.equal(store.getSession(2).messages[0].content, 'B'); assert.equal(store.getSession(2).modelId, 12)
})
await check('重启恢复全部会话模型与执行标识，不持久化聊天全文', () => {
	store.persist(); const cold = storeContext.useConversationStore()
	assert.equal(cold.getSession(1).activeMessageId, 'A-run'); assert.equal(cold.getSession(2).imageModelId, 22)
	assert.equal(cold.getSession(1).messages.length, 0); assert.equal(cold.getSession(1).streaming, false)
})
const chat = read('pages/index/ChatContent.vue')
const script = chat.match(/<script setup>([\s\S]*?)<\/script>/)[1]
const scriptAst = parseJavaScript(script, { sourceType: 'module' })
function fn(name) {
	const node = scriptAst.program.body.find((item) => item.type === 'FunctionDeclaration' && item.id.name === name)
	assert.ok(node, `missing ${name}`)
	return script.slice(node.start, node.end)
}
const events = [], streams = new Map(), polls = []
const ctx = vm.createContext({ conversationStore: store, disposed: false, sessionEpoch: 0, streams, uni,
	isCurrentSession: (id) => String(store.conversationId) === String(id), followLatestIfNearBottom: () => events.push('scroll'),
	refreshBalance: () => events.push('balance'), startConversationSync: (id) => polls.push(id), selectedClarificationOptionId: ref(''),
	startResultPolling() {}, clearConversationSync() {}, goRecharge() {}, stopChat: async (id) => events.push(`stop:${id}`),
	ACTIVE_RUN_STATES: new Set(['QUEUED', 'RUNNING', 'GENERATING']), fullUrl: (url) => url,
})
for (const name of ['sameId', 'safeParse', 'hasGenerationTasks', 'buildResult', 'mergeResultData', 'mergeTool', 'finishStream', 'createStreamHandlers', 'handleStop', 'markTaskImages', 'applyGenerationTaskSnapshot', 'applyRunSnapshot']) vm.runInContext(fn(name), ctx)
const aiA = { text: '', thinking: '', tools: [], result: null, renderKey: 'local-assistant-A' }
const aiB = { text: '', thinking: '', tools: [], result: null }
store.getSession(1).requestId = 'A'; store.getSession(1).streaming = true
store.getSession(1).messages = [aiA]
store.setConversationId(2); store.getSession(2).requestId = 'B'; store.getSession(2).streaming = true
const a = ctx.createStreamHandlers(aiA, { conversationId: 1, requestId: 'A' })
const b = ctx.createStreamHandlers(aiB, { conversationId: 2, requestId: 'B' })
streams.set('1', { abort: () => events.push('abort:A') }); streams.set('2', { abort: () => events.push('abort:B') })
await check('A 的迟到 meta/text/done 不切走 B、不结束 B 的流', () => {
	a.onMeta({ conversationId: 1, messageId: 101 }); a.onText({ content: 'A reply' }); b.onText({ content: 'B reply' }); a.onDone()
	assert.equal(store.conversationId, 2); assert.equal(store.getSession(2).streaming, true)
	assert.equal(aiA.text, 'A reply'); assert.equal(aiB.text, 'B reply'); assert.equal(streams.has('2'), true)
	assert.equal(polls.at(-1), 1); assert.equal(events.filter((x) => x === 'scroll').length, 1)
})
await check('旧请求事件不能覆盖同会话的后续请求', () => {
	store.getSession(1).requestId = 'A-new'; store.getSession(1).streaming = true
	a.onText({ content: 'stale' }); a.onDone()
	assert.equal(aiA.text, 'A reply'); assert.equal(store.getSession(1).streaming, true)
})
await check('显式停止只停止当前会话，其他会话保持运行', async () => {
	await ctx.handleStop(); assert.ok(events.includes('stop:B')); assert.ok(events.includes('abort:B'))
	assert.equal(events.includes('abort:A'), false); assert.equal(store.getSession(1).streaming, true)
})
await check('恢复状态不重复添加已有流消息、不重复发起任务，参考图部分成功保留', () => {
	const s = store.getSession(1); s.requestId = 'A'; s.messages = [aiA]
	ctx.applyRunSnapshot(s, { runId: 'A', requestId: 'A', messageId: 101, status: 'GENERATING', text: 'restored', generationTasks: [{ taskId: 'img-a', n: 2, status: 'FAILED', resultUrls: JSON.stringify(['image-one', null]) }] })
	assert.equal(s.messages.length, 1); assert.equal(s.messages[0].text, 'restored'); assert.equal(s.status, 'GENERATING')
	assert.equal(s.messages[0].result.images[0].status, 'SUCCEEDED'); assert.equal(s.messages[0].result.images[1].status, 'FAILED')
	assert.equal(store.conversationId, 2)
})
await check('HTTP 失败先恢复服务端任务状态，不误解锁或干扰其他会话', () => {
	const s = store.getSession(1); s.requestId = 'A-error'; s.streaming = true
	const ai = { text: '', thinking: '', result: null, tools: [] }
	ctx.createStreamHandlers(ai, { conversationId: 1, requestId: 'A-error' }).onError({ type: 'error', message: '模型不可用' })
	assert.equal(s.transportError, '模型不可用'); assert.equal(s.recovering, true); assert.equal(polls.at(-1), 1)
	assert.equal(store.conversationId, 2)
})
await check('SSE 正常结束仅完成一次；HTTP 拒绝和断流触发错误恢复', () => {
	let request, onChunk
	const outputs = []
	const scope = vm.createContext({ console, TextDecoder, Uint8Array, ArrayBuffer,
		config: { baseUrl: 'http://local', tokenKey: 'token' }, getStorage: () => 'test',
		uni: { request: (options) => { request = options; return { onChunkReceived: (fn) => { onChunk = fn }, abort() {} } } },
	})
	vm.runInContext(read('api/sse.js').replace(/^import .*$/gm, '').replace(/export /g, ''), scope)
	const start = () => scope.agentChatStream({ data: {}, onDone: () => outputs.push('done'), onError: (e) => outputs.push(e.type || 'recovery') })
	start(); onChunk({ data: new TextEncoder().encode('data:{"type":"done"}\n\n').buffer }); request.success({ statusCode: 200 })
	assert.deepEqual(outputs, ['done'])
	start(); request.success({ statusCode: 429, data: { code: 429, msg: '稍后重试' } }); assert.equal(outputs.at(-1), 'error')
	start(); request.success({ statusCode: 200 }); assert.equal(outputs.at(-1), 'recovery')
})
await check('多个工具任务与已有最终消息恢复时保留全部图片', () => {
	const s = store.getSession(1); s.requestId = 'images'; s.streaming = true
	const ai = { id: 901, role: 'assistant', text: '', thinking: '', tools: [], result: null }
	s.messages = [ai]
	const handler = ctx.createStreamHandlers(ai, { conversationId: 1, requestId: 'images' })
	handler.onTool({ name: 'generate_images', status: 'done', taskId: 'first', n: 2 })
	handler.onTool({ name: 'generate_images', status: 'done', taskId: 'second', n: 1 })
	handler.onResult({ data: { images: [{ taskId: 'second', seq: 1 }] } })
	assert.equal(ai.result.images.length, 3)
	ctx.applyRunSnapshot(s, { runId: 'images', assistantMessageId: 901, status: 'COMPLETED', generationTasks: [
		{ taskId: 'first', n: 2, status: 'SUCCEEDED', resultUrls: ['one', 'two'] },
		{ taskId: 'second', n: 1, status: 'SUCCEEDED', resultUrls: ['three'] },
	] })
	assert.equal(s.messages.length, 1)
	assert.deepEqual(Array.from(s.messages[0].result.images, (image) => image.url), ['one', 'two', 'three'])
})
await check('独立出图超时后相同输入复用 requestId，确认成功或改参数才换编号', () => {
	const scope = vm.createContext({ ref, uni, defineStore: (_, factory) => () => proxyRefs(factory()) })
	vm.runInContext(read('store/generation.js').replace(/^import .*$/gm, '').replace('export const useGenerationStore', 'var useGenerationStore'), scope)
	const generation = scope.useGenerationStore(), body = { modelId: 9, prompt: '海报', n: 1 }
	const first = generation.requestFor(body)
	assert.equal(generation.requestFor({ ...body }), first)
	assert.notEqual(generation.requestFor({ ...body, n: 2 }), first)
	const second = generation.requestFor(body)
	assert.equal(second, first)
	generation.acknowledgeRequest('unrelated'); assert.equal(generation.requestFor(body), second)
	generation.acknowledgeRequest(second); assert.notEqual(generation.requestFor(body), second)
})
await check('独立出图未确认请求跨重启复用编号，账号与有序参考图互相隔离', () => {
	const scope = vm.createContext({ ref, uni, defineStore: (_, factory) => () => proxyRefs(factory()) })
	vm.runInContext(read('store/generation.js').replace(/^import .*$/gm, '').replace('export const useGenerationStore', 'var useGenerationStore'), scope)
	const body = { modelId: 9, prompt: '海报', refImages: ['A', 'B'], n: 1 }
	const first = scope.useGenerationStore().requestFor(body, 100)
	const cold = scope.useGenerationStore()
	assert.equal(cold.requestFor({ n: 1, refImages: ['A', 'B'], prompt: '海报', modelId: 9 }, 100), first)
	const other = cold.requestFor(body, 200)
	assert.notEqual(other, first)
	assert.equal(scope.useGenerationStore().requestFor(body, 100), first)
	assert.notEqual(cold.requestFor({ ...body, refImages: ['B', 'A'] }, 100), first)
	assert.equal(scope.useGenerationStore().requestFor(body, 100), first)
	cold.acknowledgeRequest(other)
	assert.notEqual(scope.useGenerationStore().requestFor(body, 200), other)
})
await check('保存请求编号失败时阻止发起出图，不产生不可恢复的扣费', () => {
	const scope = vm.createContext({ ref, uni: { ...uni, setStorageSync: () => { throw Error('storage full') } }, defineStore: (_, factory) => () => proxyRefs(factory()) })
	vm.runInContext(read('store/generation.js').replace(/^import .*$/gm, '').replace('export const useGenerationStore', 'var useGenerationStore'), scope)
	assert.throws(() => scope.useGenerationStore().requestFor({ prompt: 'new' }, 300), /storage full/)
})
await check('换账号清除所有会话元数据，不回显上一位用户', () => {
	storage.set('user', { id: 2 }); assert.equal(store.syncOwner(), true)
	assert.equal(store.conversationId, null); assert.equal(store.getSession(1).messages.length, 0); assert.equal(store.getSession(1).modelId, null)
})
await check('聊天和独立出图请求均传所选模型，前端不提交费用', () => {
	const send = fn('handleSend'); assert.match(send, /modelId: conversationStore.currentSession.modelId/); assert.match(send, /imageModelId: conversationStore.currentSession.imageModelId/)
	assert.match(read('pages/create/CreateContent.vue'), /modelId: generationStore.modelId/)
	assert.doesNotMatch(send.slice(send.indexOf('const streamPayload')), /beanCosts:|beanPer|price:/)
})
await check('冷启动旧快照晚于新请求返回，不替换消息对象或清除生成态', async () => {
	for (const alreadyFinished of [false, true]) {
		store.setConversationId(501)
		const session = store.getSession(501)
		session.requestId = 'old'; session.streaming = false; session.messages = []
		let resolveHistory, replacements = 0
		const delayedHistory = new Promise((resolve) => { resolveHistory = resolve })
		const currentStreams = new Map()
		const scope = vm.createContext({ conversationStore: store, disposed: false, sessionEpoch: 0, historySwitchVersion: 0,
			streams: currentStreams, getMessagePage: () => delayedHistory,
			getPendingClarification: async () => null, getConversationRun: async () => ({ requestId: 'old', status: 'COMPLETED' }),
			applyConversationSnapshot: () => { replacements++ }, applyRunSnapshot: () => { replacements++; session.streaming = false },
			ACTIVE_RUN_STATES: new Set(), startConversationSync() {}, historyCursor: ref(null), historyHasMore: ref(false),
			restoreInputDraft() {}, scrollToBottom() {}, uni,
		})
		vm.runInContext(fn('loadConversation'), scope)
		const loading = scope.loadConversation(501)
		session.requestId = 'new'; session.streaming = !alreadyFinished
		session.messages.push({ role: 'assistant', text: 'new stream' })
		const liveMessage = session.messages[0]
		if (!alreadyFinished) currentStreams.set('501', {})
		resolveHistory({ records: [{ id: 1, content: 'old snapshot' }] })
		assert.equal(await loading, false)
		assert.equal(replacements, 0); assert.equal(session.messages[0], liveMessage)
		assert.equal(session.streaming, !alreadyFinished)
	}
})
await check('旧账号的模型目录晚返回时不覆盖新账号列表和选择', async () => {
	let resolveOld, resolveNew, selections = 0
	const oldResponse = new Promise((resolve) => { resolveOld = resolve })
	const newResponse = new Promise((resolve) => { resolveNew = resolve })
	const responses = [oldResponse, newResponse]
	const scope = vm.createContext({ userStore: { isLoggedIn: true }, sessionEpoch: 0, modelsLoadVersion: 0, disposed: false,
		modelsLoading: ref(false), chatModels: ref([]), imageModels: ref([]),
		getAvailableModels: () => responses.shift(), ensureModelSelection: () => { selections++ },
	})
	vm.runInContext(fn('loadModels'), scope)
	const old = scope.loadModels(); scope.sessionEpoch = 1; const fresh = scope.loadModels()
	resolveNew({ chatModels: [{ modelId: 2 }], imageModels: [] }); await fresh
	resolveOld({ chatModels: [{ modelId: 1 }], imageModels: [] }); await old
	assert.equal(scope.chatModels.value[0].modelId, 2); assert.equal(selections, 1); assert.equal(scope.modelsLoading.value, false)
})
await check('冷启动待充值恢复错误文案、原消息续作记录，付款就绪状态不被覆盖', () => {
	const session = store.getSession(601); session.messages = []; session.pendingResumeTask = null
	const run = { runId: 'recharge', requestId: 'recharge', conversationId: 601, messageId: 701,
		modelId: 11, imageModelId: 21, status: 'WAITING_RECHARGE', errorMessage: '豆子余额不足，请充值后再聊' }
	ctx.applyRunSnapshot(session, run)
	assert.equal(session.messages.length, 1); assert.match(session.messages[0].text, /豆子余额不足/)
	assert.equal(session.messages[0].rechargeRequired, true); assert.equal(session.recovering, false)
	assert.equal(session.pendingResumeTask.sourceMessageId, 701); assert.equal(session.pendingResumeTask.ready, false)
	session.pendingResumeTask.ready = true; ctx.applyRunSnapshot(session, run)
	assert.equal(session.pendingResumeTask.ready, true); assert.equal(session.messages.length, 1)
	ctx.applyRunSnapshot(session, { ...run, status: 'COMPLETED', text: '已完成' })
	assert.equal(session.pendingResumeTask, null); assert.equal(session.messages[0].rechargeRequired, false)
})
await check('无已保存消息的余额拒绝只提供充值提示，不构造重复消息续作', () => {
	const session = store.getSession(602); session.messages = []
	session.pendingResumeTask = { sourceMessageId: 999, ready: true }
	ctx.applyRunSnapshot(session, { runId: 'before-message', status: 'WAITING_RECHARGE', messageId: null })
	assert.match(session.messages[0].text, /豆子余额不足/); assert.equal(session.messages[0].rechargeRequired, true)
	assert.equal(session.pendingResumeTask, null); assert.equal(session.streaming, false); assert.equal(session.recovering, false)
})
await check('恢复检查发现原消息已完成时，不使用旧充值记录再次发起续作', async () => {
	store.setConversationId(603)
	store.setPendingResumeTask({ conversationId: 603, sourceMessageId: 703, ready: true })
	let submitted = false
	const scope = vm.createContext({ conversationStore: store, interactionLocked: ref(false),
		loadConversation: async () => { store.clearPendingResumeTask(603); return true },
		createRequestId: () => { submitted = true; throw Error('must not resume completed message') },
		sameId: ctx.sameId,
	})
	vm.runInContext(fn('tryResumePendingTask'), scope)
	await scope.tryResumePendingTask()
	assert.equal(submitted, false)
})
await check('出图预算不足充值后新增确认消费原范围，保留原卡片及所选模型', async () => {
	store.setConversationId(604)
	const session = store.getSession(604)
	session.messages = []; session.pendingResumeTask = null
	const run = { runId: 'image-budget', requestId: 'image-budget', conversationId: 604, messageId: 704,
		modelId: 11, imageModelId: 21, status: 'WAITING_CONFIRMATION',
		clarification: { key: 'INSUFFICIENT_BALANCE', scopeId: 'original-scope', question: '需要充值后继续' } }
	ctx.applyRunSnapshot(session, run)
	assert.equal(session.pendingResumeTask.sourceMessageId, 704)
	assert.equal(session.pendingResumeTask.clarificationScopeId, 'original-scope')
	assert.equal(session.pendingResumeTask.clarificationOptionId, 'generate_requested')
	assert.equal(session.messages[0].rechargeRequired, true)
	session.pendingResumeTask.ready = true
	// The latest server snapshot may restore fields that are missing from old local metadata.
	session.pendingResumeTask.clarificationScopeId = undefined
	let submitted
	const scope = vm.createContext({ conversationStore: store, interactionLocked: ref(false), sameId: ctx.sameId,
		loadConversation: async () => { ctx.applyRunSnapshot(session, run); return true },
		createRequestId: () => 'resume-image-budget', scrollToBottom() {}, streams: new Map(),
		createStreamHandlers: () => ({}), chatStream: (payload) => { submitted = payload; return {} },
		resumeChatStream: () => { throw Error('budget clarification must create a new confirmation') },
	})
	vm.runInContext(fn('tryResumePendingTask'), scope)
	await scope.tryResumePendingTask()
	assert.equal(submitted.sourceMessageId, undefined); assert.equal(submitted.conversationId, 604)
	assert.equal(submitted.clarificationScopeId, 'original-scope'); assert.equal(submitted.clarificationOptionId, 'generate_requested')
	assert.equal(submitted.message, '充值已到账，请按原计划继续生成'); assert.equal(session.messages.length, 3)
	assert.equal(submitted.modelId, 11); assert.equal(submitted.imageModelId, 21)
	assert.equal(session.messages[0].clarification.scopeId, 'original-scope')
	assert.equal(session.messages[1].role, 'user'); assert.equal(session.pendingResumeTask, null)
	ctx.applyRunSnapshot(session, { ...run, requestId: 'resume-image-budget', status: 'RUNNING', clarification: null })
	assert.equal(session.pendingResumeTask, null)
})
await check('最低余额不足且尚无assistant时仍续作已保存的原消息，不新建确认', async () => {
	store.setConversationId(605)
	const session = store.getSession(605); session.messages = []; session.pendingResumeTask = null
	const run = { runId: 'text-budget', conversationId: 605, messageId: 705, status: 'WAITING_RECHARGE', modelId: 11, imageModelId: 21 }
	ctx.applyRunSnapshot(session, run); session.pendingResumeTask.ready = true
	let submitted
	const scope = vm.createContext({ conversationStore: store, interactionLocked: ref(false), sameId: ctx.sameId,
		loadConversation: async () => { ctx.applyRunSnapshot(session, run); return true },
		createRequestId: () => 'resume-text-budget', scrollToBottom() {}, streams: new Map(), createStreamHandlers: () => ({}),
		chatStream: () => { throw Error('unanswered message must resume without new user confirmation') },
		resumeChatStream: (payload) => { submitted = payload; return {} },
	})
	vm.runInContext(fn('tryResumePendingTask'), scope); await scope.tryResumePendingTask()
	assert.equal(submitted.sourceMessageId, 705); assert.equal(submitted.message, undefined)
	assert.equal(submitted.clarificationScopeId, undefined); assert.equal(session.messages.length, 1)
})
await check('切换 A→B→A 和新建会话保留两个实时流，不中断或重拉覆盖消息', async () => {
	const a = store.getSession(801), b = store.getSession(802)
	a.messages = [{ role: 'assistant', text: 'A partial' }]; b.messages = [{ role: 'assistant', text: 'B partial' }]
	a.streaming = true; b.streaming = true
	const streams = new Map([['801', { abort() { throw Error('A must continue') } }], ['802', { abort() { throw Error('B must continue') } }]])
	const scope = vm.createContext({ conversationStore: store, streams, disposed: false, sessionEpoch: 0, historySwitchVersion: 0,
		historyCursor: ref(null), historyHasMore: ref(false), restoreInputDraft() {}, scrollToBottom() {}, uni,
		getMessagePage() { throw Error('must preserve active stream messages') },
		preparingConversation: ref(false), newConversationSwitching: ref(false), historyOlderLoading: ref(false),
		scrollCommandVersion: 0, scrollIntoView: ref(''), saveInputDraft() {}, commandScrollTop() {}, nextTick: async () => {}, revealPreparedConversation: async () => {},
	})
	vm.runInContext(fn('loadConversation') + '\n' + fn('onHistoryNew'), scope)
	assert.equal(await scope.loadConversation(801), true); const liveA = store.messages[0]
	assert.equal(await scope.loadConversation(802), true); b.messages[0].text += ' done'
	assert.equal(await scope.loadConversation(801), true); assert.equal(store.messages[0], liveA)
	await scope.onHistoryNew()
	assert.equal(store.conversationId, null); assert.equal(a.streaming, true); assert.equal(b.streaming, true)
	assert.equal(streams.size, 2); assert.equal(b.messages[0].text, 'B partial done')
})
await check('恢复查询锁按会话隔离，失败释放；旧查询结束不释放新查询的锁', async () => {
	store.setConversationId(803)
	const session = store.getSession(803), other = store.getSession(804)
	const deferred = [], scope = vm.createContext({ conversationStore: store, streams: new Map(), disposed: false, sessionEpoch: 0, historySwitchVersion: 0,
		getMessagePage: () => new Promise((resolve, reject) => deferred.push({ resolve, reject })),
		getPendingClarification: async () => null, getConversationRun: async () => null, uni,
		applyConversationSnapshot() {}, applyRunSnapshot() {}, ACTIVE_RUN_STATES: new Set(), startConversationSync() {},
		historyCursor: ref(null), historyHasMore: ref(false), restoreInputDraft() {}, scrollToBottom() {},
	})
	vm.runInContext(fn('loadConversation'), scope)
	const old = scope.loadConversation(803); assert.equal(session.loadingHistory, true); assert.notEqual(other.loadingHistory, true)
	const newer = scope.loadConversation(803); deferred[0].reject(Error('old timeout'))
	assert.equal(await old, false); assert.equal(session.loadingHistory, true)
	deferred[1].reject(Error('new timeout')); assert.equal(await newer, false); assert.equal(session.loadingHistory, false)
	assert.match(script, /conversationStore.currentSession.loadingHistory === true/)
})
await check('旧账号列表失败与同账号乱序响应不清空或覆盖最新列表状态', async () => {
	const jobs = []
	const scope = vm.createContext({ conversationStore: store, streams: new Map(), disposed: false, sessionEpoch: 0,
		conversationListVersion: 0, historyLoading: ref(false), conversationRecords: ref([]), sameId: ctx.sameId,
		listConversations: () => new Promise((resolve, reject) => jobs.push({ resolve, reject })),
	})
	vm.runInContext(fn('loadConversations'), scope)
	const old = scope.loadConversations(); scope.sessionEpoch = 1; const fresh = scope.loadConversations()
	jobs[1].resolve({ records: [{ conversationId: 811, runId: 'new', runStatus: 'GENERATING' }] }); await fresh
	jobs[0].reject(Error('old account failed')); await old
	assert.equal(scope.conversationRecords.value[0].conversationId, 811); assert.equal(scope.historyLoading.value, false)
	const first = scope.loadConversations(), second = scope.loadConversations()
	jobs[3].resolve({ records: [{ conversationId: 812, runStatus: 'COMPLETED' }] }); await second
	jobs[2].resolve({ records: [{ conversationId: 811, runStatus: 'RUNNING' }] }); await first
	assert.equal(scope.conversationRecords.value[0].conversationId, 812)
})
await check('会话列表旧 run 状态不覆盖当前新 run，匹配 run 可恢复出图完成状态', async () => {
	const session = store.getSession(813); session.requestId = 'latest'; session.status = 'GENERATING'
	let response = { records: [{ conversationId: 813, runId: 'old', runStatus: 'COMPLETED' }] }
	const scope = vm.createContext({ conversationStore: store, streams: new Map(), disposed: false, sessionEpoch: 0,
		conversationListVersion: 0, historyLoading: ref(false), conversationRecords: ref([]), sameId: ctx.sameId,
		listConversations: async () => response,
	})
	vm.runInContext(fn('loadConversations'), scope)
	await scope.loadConversations(); assert.equal(session.status, 'GENERATING')
	response = { records: [{ conversationId: 813, runId: 'latest', runStatus: 'COMPLETED' }] }
	await scope.loadConversations(); assert.equal(session.status, 'COMPLETED')
})
await check('新建请求途中切换账号或会话时，迟到响应不抢当前会话也不发送旧输入', async () => {
	for (const accountChanged of [false, true]) {
		store.reset(); store.setModels(11, 21)
		let resolveCreated, sent = false
		const scope = vm.createContext({ conversationStore: store, disposed: false, sessionEpoch: 0, preparingConversation: ref(false),
			interactionLocked: ref(false), isReferenceUploadPending: () => false, hasGeneratingImage: ref(false), inputText: ref('old prompt'),
			pendingImages: ref([]), activeSkill: ref(''), selectedClarificationOptionId: ref(''),
			requireLogin: () => true, uni, createConversation: () => new Promise((resolve) => { resolveCreated = resolve }),
			createRequestId: () => { sent = true; throw Error('must not send detached input') },
		})
		vm.runInContext(fn('handleSend'), scope)
		const pending = scope.handleSend()
		store.setConversationId(815)
		if (accountChanged) { scope.sessionEpoch = 1; scope.preparingConversation.value = false }
		resolveCreated({ conversationId: accountChanged ? 817 : 816 }); await pending
		assert.equal(store.conversationId, 815); assert.equal(sent, false); assert.equal(scope.preparingConversation.value, false)
		if (accountChanged) assert.equal(store.sessions['817'], undefined)
		else { assert.equal(store.getSession(816).modelId, 11); assert.equal(store.getSession(816).draft.text, 'old prompt') }
	}
})
await check('迟到创建保留本轮文本、参考图顺序与引用、skill 和已选选项', async () => {
	store.reset()
	let resolveCreated
	const originalImages = [{ type: 'upload', url: '/reference-one.png' }, { type: 'generated', taskId: 818, seq: 2, url: '/reference-two.png' }]
	const scope = vm.createContext({ conversationStore: store, disposed: false, sessionEpoch: 0, preparingConversation: ref(false),
		interactionLocked: ref(false), isReferenceUploadPending: () => false, hasGeneratingImage: ref(false), inputText: ref('original prompt'),
		pendingImages: ref(originalImages.slice()), activeSkill: ref('portrait'), selectedClarificationOptionId: ref('keep-original'),
		requireLogin: () => true, uni, createConversation: () => new Promise((resolve) => { resolveCreated = resolve }),
		createRequestId: () => { throw Error('must not send detached input') },
	})
	vm.runInContext(fn('handleSend'), scope)
	const pending = scope.handleSend()
	store.setConversationId(819)
	scope.inputText.value = 'new session prompt'; scope.pendingImages.value = [{ type: 'upload', url: '/new-session.png' }]
	scope.activeSkill.value = 'different skill'; scope.selectedClarificationOptionId.value = 'different-option'
	resolveCreated({ conversationId: 820 }); await pending
	const draft = store.getSession(820).draft
	assert.equal(store.conversationId, 819); assert.equal(draft.text, 'original prompt')
	assert.equal(JSON.stringify(draft.images), JSON.stringify(originalImages))
	assert.equal(draft.skill, 'portrait'); assert.equal(draft.option, 'keep-original')
	assert.equal(scope.inputText.value, 'new session prompt'); assert.equal(scope.pendingImages.value[0].url, '/new-session.png')
})
await check('Vue 脚本与模板可编译', () => {
	for (const file of ['pages/index/ChatContent.vue', 'pages/index/components/HistoryOverlay.vue', 'pages/create/CreateContent.vue', 'components/common/ModelSelector.vue']) {
		const { descriptor, errors } = parse(read(file), { filename: file }); assert.equal(errors.length, 0)
		const compiled = compileScript(descriptor, { id: file })
		const result = compileTemplate({ source: descriptor.template.content, filename: file, id: file, compilerOptions: { bindingMetadata: compiled.bindings } })
		assert.equal(result.errors.length, 0, `${file}: ${result.errors.join(', ')}`)
	}
})
console.log(`${passed} 项多会话与模型选择验证通过`)

/** Per-conversation state. Messages stay in memory; server runs recover after relaunch. */
import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import config from '@/utils/config.js'

const STORAGE_KEY = 'agent_conversation_sessions_v2'
function read(key) { try { return uni.getStorageSync(key) || null } catch { return null } }
function write(key, value) { try { uni.setStorageSync(key, value) } catch { /* Server remains authoritative. */ } }
function ownerId() { const user = read(config.userInfoKey); return String(user?.id || user?.userId || '') }
function newSession(id, saved = {}) {
	return { conversationId: id, messages: [], streaming: false, recovering: false,
		activeMessageId: saved.activeMessageId || null, requestId: null, currentThinking: '',
		modelId: saved.modelId || null, imageModelId: saved.imageModelId || null,
		pendingResumeTask: saved.pendingResumeTask || null, status: saved.status || '',
		draft: null, pendingClarification: null, nextCursor: null, hasMore: false }
}
export const useConversationStore = defineStore('conversation', () => {
	const owner = ref(ownerId())
	const stored = read(STORAGE_KEY)
	const saved = stored?.owner === owner.value ? stored : {}
	const conversationId = ref(saved.activeId || null)
	const sessions = reactive({})
	Object.entries(saved.sessions || {}).forEach(([key, value]) => { sessions[key] = newSession(value.conversationId, value) })
	const promptDraft = ref(null)
	function getSession(id = conversationId.value) {
		const key = id == null ? 'draft' : String(id)
		if (!sessions[key]) sessions[key] = newSession(id)
		return sessions[key]
	}
	const currentSession = computed(() => getSession())
	function persist() {
		const metadata = {}
		Object.entries(sessions).forEach(([key, s]) => { metadata[key] = {
			conversationId: s.conversationId, activeMessageId: s.activeMessageId,
			modelId: s.modelId, imageModelId: s.imageModelId, pendingResumeTask: s.pendingResumeTask, status: s.status,
		} })
		write(STORAGE_KEY, { owner: owner.value, activeId: conversationId.value, sessions: metadata })
	}
	function syncOwner() {
		const next = ownerId()
		if (next === owner.value) return false
		Object.keys(sessions).forEach((key) => delete sessions[key])
		owner.value = next; conversationId.value = null; promptDraft.value = null; persist()
		return true
	}
	const field = (name) => computed({ get: () => currentSession.value[name], set: (value) => { currentSession.value[name] = value } })
	const messages = field('messages'), streaming = field('streaming'), currentThinking = field('currentThinking')
	const activeMessageId = field('activeMessageId'), pendingResumeTask = field('pendingResumeTask')
	function setConversationId(id) {
		// A newly created server conversation inherits the draft model selection.
		if (conversationId.value == null && id != null && !sessions[String(id)]) {
			const draft = getSession(null)
			sessions[String(id)] = newSession(id, draft)
		}
		conversationId.value = id; getSession(id); persist()
	}
	function setActiveMessageId(id, conversation = conversationId.value) { getSession(conversation).activeMessageId = id; persist() }
	function setStreaming(value, conversation = conversationId.value) { getSession(conversation).streaming = value }
	function setModels(modelId, imageModelId, conversation = conversationId.value) {
		const s = getSession(conversation); s.modelId = modelId; s.imageModelId = imageModelId; persist()
	}
	function replaceMessages(list) { currentSession.value.messages = Array.isArray(list) ? list : [] }
	function replaceConversationSnapshot(id, list) { setConversationId(id); replaceMessages(list) }
	function addMessage(message) { currentSession.value.messages.push(message) }
	function appendText(text) { const last = messages.value[messages.value.length - 1]; if (last) last.content = (last.content || '') + text }
	function prependMessages(list) {
		const ids = new Set(messages.value.map((m) => m.renderKey || m.id))
		messages.value = (list || []).filter((m) => !ids.has(m.renderKey || m.id)).concat(messages.value)
	}
	function setPendingResumeTask(task, conversation = task?.conversationId ?? conversationId.value) { getSession(conversation).pendingResumeTask = task || null; persist() }
	function clearPendingResumeTask(conversation = conversationId.value) { setPendingResumeTask(null, conversation) }
	function markPendingResumeReady() { if (pendingResumeTask.value) setPendingResumeTask({ ...pendingResumeTask.value, ready: true }) }
	function setPromptDraft(content) { promptDraft.value = typeof content === 'string' ? content.trim() : null }
	function takePromptDraft() { const value = promptDraft.value; promptDraft.value = null; return value }
	function reset() { sessions.draft = newSession(null); conversationId.value = null; persist() }
	return { conversationId, sessions, currentSession, messages, streaming, currentThinking, activeMessageId,
		pendingResumeTask, promptDraft, getSession, persist, syncOwner, setModels, setConversationId,
		setActiveMessageId, setStreaming, replaceMessages, replaceConversationSnapshot, addMessage, appendText,
		prependMessages, setPendingResumeTask, clearPendingResumeTask, markPendingResumeReady, setPromptDraft, takePromptDraft, reset }
})

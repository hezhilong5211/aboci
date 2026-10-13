/**
 * 会话状态管理
 * 管理当前对话 id、消息列表、流式输出态
 * 消费方:批2 对话流式渲染(当前 actions 为占位)
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

const ACTIVE_CONVERSATION_KEY = 'agent_active_conversation_id'
const ACTIVE_MESSAGE_KEY = 'agent_active_message_id'
const PENDING_RESUME_KEY = 'agent_pending_resume_task'

function readStoredId(key) {
	try {
		const value = uni.getStorageSync(key)
		return value === '' || value == null ? null : value
	} catch (e) {
		return null
	}
}

function persistId(key, value) {
	try {
		if (value === '' || value == null) {
			uni.removeStorageSync(key)
		} else {
			uni.setStorageSync(key, value)
		}
	} catch (e) {
		// 本地存储异常不阻断对话主流程。
	}
}

function readStoredObject(key) {
	try {
		const value = uni.getStorageSync(key)
		return value && typeof value === 'object' ? value : null
	} catch (e) {
		return null
	}
}

function persistObject(key, value) {
	try {
		if (value) uni.setStorageSync(key, value)
		else uni.removeStorageSync(key)
	} catch (e) {
		// 本地存储异常不阻断对话主流程。
	}
}

export const useConversationStore = defineStore('conversation', () => {
	// ====== 状态 ======

	/** 当前会话 id */
	const conversationId = ref(readStoredId(ACTIVE_CONVERSATION_KEY))
	/** 当前执行标识：排队时为 requestId，用户消息落库后替换为 messageId */
	const activeMessageId = ref(readStoredId(ACTIVE_MESSAGE_KEY))
	/** 豆子不足后等待支付并恢复的任务。 */
	const pendingResumeTask = ref(readStoredObject(PENDING_RESUME_KEY))
	/** 从“我的收藏”回填的一次性草稿，仅保存在当前运行内存中。 */
	const promptDraft = ref(null)
	/** 消息列表 */
	const messages = ref([])
	/** 是否正在流式输出 */
	const streaming = ref(false)
	/** 当前思考过程文本 */
	const currentThinking = ref('')

	// ====== 方法 ======

	/**
	 * 追加一条消息
	 * @param {Object} m
	 */
	function addMessage(m) {
		messages.value.push(m)
	}

	/**
	 * 向最后一条消息追加文本(流式拼接)
	 * @param {string} t
	 */
	function appendText(t) {
		const last = messages.value[messages.value.length - 1]
		if (last) {
			last.content = (last.content || '') + t
		}
	}

	/**
	 * 设置流式输出态
	 * @param {boolean} b
	 */
	function setStreaming(b) {
		streaming.value = b
	}

	/**
	 * 设置会话 id
	 * @param {string|null} id
	 */
	function setConversationId(id) {
		conversationId.value = id
		persistId(ACTIVE_CONVERSATION_KEY, id)
	}

	/**
	 * 设置仍在服务端执行的请求/消息标识
	 * @param {string|number|null} id
	 */
	function setActiveMessageId(id) {
		activeMessageId.value = id
		persistId(ACTIVE_MESSAGE_KEY, id)
	}

	/**
	 * 用服务端快照替换当前消息列表
	 * @param {Array} list
	 */
	function replaceMessages(list) {
		messages.value = Array.isArray(list) ? list : []
	}

	/**
	 * 原子切换当前会话快照，避免会话编号和消息列表分帧更新。
	 */
	function replaceConversationSnapshot(id, list) {
		setConversationId(id)
		messages.value = Array.isArray(list) ? list : []
	}

	/**
	 * 向当前消息列表前插更早历史，按消息编号去重。
	 */
	function prependMessages(list) {
		if (!Array.isArray(list) || !list.length) return
		const existing = new Set(
			messages.value.map((item) => item && (item.renderKey || item.id)).filter(Boolean)
		)
		const older = list.filter((item) => {
			const key = item && (item.renderKey || item.id)
			return key == null || !existing.has(key)
		})
		if (older.length) messages.value = older.concat(messages.value)
	}

	function setPendingResumeTask(task) {
		pendingResumeTask.value = task || null
		persistObject(PENDING_RESUME_KEY, pendingResumeTask.value)
	}

	function markPendingResumeReady() {
		if (!pendingResumeTask.value) return
		setPendingResumeTask({ ...pendingResumeTask.value, ready: true })
	}

	function clearPendingResumeTask() {
		setPendingResumeTask(null)
	}

	function setPromptDraft(content) {
		promptDraft.value = typeof content === 'string' && content.trim()
			? content.trim()
			: null
	}

	function takePromptDraft() {
		const draft = promptDraft.value
		promptDraft.value = null
		return draft
	}

	/**
	 * 重置会话态(开启新对话)
	 */
	function reset() {
		setConversationId(null)
		setActiveMessageId(null)
		messages.value = []
		streaming.value = false
		currentThinking.value = ''
	}

	return {
		conversationId,
		activeMessageId,
		pendingResumeTask,
		promptDraft,
		messages,
		streaming,
		currentThinking,
		addMessage,
		appendText,
		setStreaming,
		setConversationId,
		setActiveMessageId,
		replaceMessages,
		replaceConversationSnapshot,
		prependMessages,
		setPendingResumeTask,
		markPendingResumeReady,
		clearPendingResumeTask,
		setPromptDraft,
		takePromptDraft,
		reset
	}
})

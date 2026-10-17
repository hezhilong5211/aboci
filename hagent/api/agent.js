/**
 * Agent 对话相关 API
 * 对接后端 AgentChatController（/api/mobile/agent）
 * 流式对话转调 sse.js，非流式接口走 request 的 get/post
 */

import { del, get, post } from './request.js'
import { agentChatStream } from './sse.js'

/**
 * 流式对话
 * @param {Object} params - { requestId, conversationId?, modelId?, imageModelId?, imageResolution?, message, imageInputs?, skill?, clarificationScopeId?, clarificationOptionId? }
 * @param {Object} handlers - { onMeta, onQueue, onThinking, onText, onTool, onClarification, onResult, onDone, onError }
 * @returns {Object} 含 abort() 的对象（供调用方中断）
 */
export function chatStream(params, handlers) {
	return agentChatStream({ data: params, ...handlers })
}

/** 充值到账后恢复已落库的原用户消息，不重复新增历史消息。 */
export function resumeChatStream(params, handlers) {
	return agentChatStream({
		endpoint: '/api/mobile/agent/chat/resume',
		data: params,
		...handlers,
	})
}

/** 显式停止仍在执行的模型/工具循环：排队时传 requestId，落库后传 meta.messageId。 */
export function stopChat(runId) {
	return post(`/api/mobile/agent/chat/${runId}/stop`)
}

/**
 * 独立出图（生成页 C 组）：文生图 / 图生图
 * @param {Object} body - { prompt, aspectRatio?, resolution?, n(1-9), refImages?(最多9张,图生图用) }
 * @returns {Promise<Object>} - { taskId }，前端用 getGeneration 轮询出图结果
 */
export function generateImage(body) {
	return post('/api/mobile/agent/generate', body)
}

/** 查询独立生成页面和下载水印开关（免登录）。 */
export function getGenerationConfig() {
	return get('/api/mobile/generation/config')
}

/**
 * 查询生成任务结果（出图/视频等异步任务）
 * @param {string} taskId - 任务 ID
 */
export function getGeneration(taskId, options = {}) {
	return get('/api/mobile/agent/generation/' + taskId, {}, options)
}

/** 查询后台默认图像模型允许的分辨率选项。 */
export function getGenerationOptions(modelId) {
	return get('/api/mobile/agent/generation/options', modelId == null ? {} : { modelId })
}

/** 按当前后台水印配置获取本人任务中指定图片的下载地址。 */
export function getGenerationDownloadInfo(taskId, seq) {
	return get(`/api/mobile/agent/generation/${taskId}/items/${seq}/download-info`)
}

/**
 * 分页查询会话列表
 * @param {number} [pageNum=1] - 页码
 * @param {number} [pageSize=20] - 每页数量
 */
export function listConversations(pageNum = 1, pageSize = 20) {
	return get('/api/mobile/agent/conversations', { pageNum, pageSize })
}

/**
 * 创建会话
 * @param {string} title - 会话标题
 */
export function createConversation(title) {
	return post('/api/mobile/agent/conversations', { title })
}

/**
 * 查询会话内的消息列表
 * @param {string|number} id - 会话 ID
 */
export function getMessages(id, options = {}) {
	return get('/api/mobile/agent/conversations/' + id + '/messages', {}, options)
}

/**
 * 游标分页查询会话消息。首次不传 beforeId 返回最近一页，继续向前加载时传 nextCursor。
 */
export function getMessagePage(id, beforeId, pageSize = 30, options = {}) {
	const params = { pageSize }
	if (beforeId != null) params.beforeId = beforeId
	return get('/api/mobile/agent/conversations/' + id + '/messages/page', params, options)
}

/** 查询某条用户消息触发的出图任务，用于断线后恢复真实图片占位。 */
export function getMessageGenerations(conversationId, messageId, options = {}) {
	return get(
		`/api/mobile/agent/conversations/${conversationId}/messages/${messageId}/generations`,
		{},
		options
	)
}

/** 查询历史会话中仍可回答的结构化澄清。 */
export function getPendingClarification(id, options = {}) {
	return get(`/api/mobile/agent/conversations/${id}/pending-clarification`, {}, options)
}

/**
 * 分页查询「我的作品」（仅成功出图任务，按创建时间倒序）
 * @param {number} [pageNum=1] - 页码
 * @param {number} [pageSize=20] - 每页数量
 * @param {string} [sourceType] - 来源过滤：CREATE_PAGE / AGENT；不传返回全部
 * @returns {Promise<Object>} - IPage，含 records（WorkVO 数组）、total
 */
export function getWorks(pageNum = 1, pageSize = 20, sourceType) {
	const params = { pageNum, pageSize }
	if (sourceType) params.sourceType = sourceType
	return get('/api/mobile/agent/works', params)
}

/** 查询一个作品批次的完整图片槽位、提示词、参考图和参数。 */
export function getWorkBatchDetail(taskId) {
	return get(`/api/mobile/agent/works/${taskId}`)
}

/** 查询单张作品的原提示词、参考图和生成参数。 */
export function getWorkItemDetail(taskId, seq) {
	return get(`/api/mobile/agent/works/${taskId}/items/${seq}`)
}

/** 按原参数重新生成单张作品。 */
export function regenerateWorkItem(taskId, seq) {
	return post(`/api/mobile/agent/works/${taskId}/items/${seq}/regenerate`)
}

/** 仅重试对话中失败的单张图片。 */
export function retryFailedImage(taskId, seq) {
	return post(`/api/mobile/agent/generation/${taskId}/items/${seq}/retry`)
}

/**
 * 查询可用技能列表
 */
export function getSkills() {
	return get('/api/mobile/agent/skills')
}

/**
 * 查询用户自定义提示词
 */
export function getUserPrompt() {
	return get('/api/mobile/agent/user-prompt')
}

/**
 * 保存用户自定义提示词
 * @param {string} content - 提示词内容
 */
export function saveUserPrompt(content) {
	return post('/api/mobile/agent/user-prompt', { content })
}

/** 收藏一条本人已发送的文本提示词。 */
export function createPromptFavorite(name, content) {
	return post('/api/mobile/agent/prompt-favorites', { name, content })
}

/** 分页查询本人提示词收藏。 */
export function listPromptFavorites(pageNum = 1, pageSize = 10) {
	return get('/api/mobile/agent/prompt-favorites', { pageNum, pageSize })
}

/** 删除本人一条提示词收藏。 */
export function deletePromptFavorite(id) {
	return del(`/api/mobile/agent/prompt-favorites/${id}`)
}

/** Enabled user-facing models, with server-owned capabilities and bean prices. */
export function getAvailableModels() {
	return get('/api/mobile/agent/models')
}

/** Durable run snapshot; reading it never starts or charges a task. */
export function getConversationRun(id, options = {}) {
	return get(`/api/mobile/agent/conversations/${id}/run`, {}, options)
}

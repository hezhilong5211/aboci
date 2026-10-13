/**
 * 生成状态管理
 * 管理出图/视频生成模式、参数、参考图、任务与结果
 * 消费方:批3 生成功能(当前 actions 为占位)
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useGenerationStore = defineStore('generation', () => {
	// ====== 状态 ======

	/** 生成模式(text2img / img2img) */
	const mode = ref('text2img')
	/** 生成参数 */
	const params = ref({ aspectRatio: '3:4', n: 1, resolution: '' })
	/** 提示词(案例「用此模板」带入 / 生成页输入) */
	const prompt = ref('')
	/** 参考图列表 */
	const refImages = ref([])
	/** 生成任务列表 */
	const tasks = ref([])
	/** 生成结果列表 */
	const results = ref([])
	/** 生成结果预览图列表，与 results 按索引对应 */
	const resultPreviewUrls = ref([])
	/** 当前结果所属任务，详情查询必须与结果槽位一起保留 */
	const resultTaskId = ref('')
	/** 案例带入标记:案例页置 true,生成页消费后置 false(触发预填) */
	const broughtIn = ref(false)

	// ====== 方法 ======

	/**
	 * 设置生成模式
	 * @param {string} m
	 */
	function setMode(m) {
		mode.value = m
	}

	/**
	 * 设置生成参数(局部合并)
	 * @param {Object} partial - 要更新的参数字段
	 */
	function setParams(partial) {
		params.value = { ...params.value, ...partial }
	}

	/**
	 * 追加一张参考图
	 * @param {*} img
	 */
	function addRefImage(img) {
		if (!img || refImages.value.length >= 9) return
		refImages.value.push(img)
	}

	/**
	 * 删除指定参考图
	 * @param {number} index
	 */
	function removeRefImage(index) {
		if (index < 0 || index >= refImages.value.length) return
		refImages.value.splice(index, 1)
	}

	/**
	 * 按原索引排列重排参考图，非完整排列直接忽略。
	 * @param {number[]} order
	 */
	function reorderRefImages(order) {
		if (!Array.isArray(order) || order.length !== refImages.value.length) return
		const unique = new Set(order)
		if (unique.size !== refImages.value.length) return
		if (order.some((index) => !Number.isInteger(index) || index < 0 || index >= refImages.value.length)) return
		const current = refImages.value.slice()
		refImages.value = order.map((index) => current[index])
	}

	/**
	 * 追加一个生成任务
	 * @param {Object} task
	 */
	function addTask(task) {
		tasks.value.push(task)
	}

	/**
	 * 设置生成结果列表
	 * @param {Array} list
	 * @param {Array} previewList
	 */
	function setResults(list, previewList = [], taskId) {
		results.value = Array.isArray(list) ? list : []
		resultPreviewUrls.value = Array.isArray(previewList) ? previewList : []
		if (taskId !== undefined) resultTaskId.value = taskId || ''
	}

	/** 从作品详情完整恢复输入状态，不改变参考图顺序，也不自动发送。 */
	function restoreWorkItem(detail = {}) {
		if (detail.replaySupported === false) return false
		prompt.value = detail.prompt || ''
		mode.value = detail.mode === 'img2img' ? 'img2img' : 'text2img'
		params.value = {
			...params.value,
			n: 1,
			resolution: detail.resolution || '',
			aspectRatio: detail.aspectRatio || '3:4',
		}
		refImages.value = Array.isArray(detail.referenceImages)
			? detail.referenceImages.slice(0, 9)
			: []
		return true
	}

	/**
	 * 设置提示词
	 * @param {string} t
	 */
	function setPrompt(t) {
		prompt.value = t || ''
	}

	/**
	 * 案例「用此模板」带入:写入 prompt/mode/张数/画幅/参考图并置带入标记,生成页据此预填
	 * @param {Object} payload - { prompt?, mode?, n?, aspectRatio?, resolution?, refImages? }
	 */
	function applyTemplate(payload = {}) {
		if (payload.prompt != null) prompt.value = payload.prompt
		if (payload.mode) mode.value = payload.mode
		if (payload.n != null) params.value = { ...params.value, n: payload.n }
		if (payload.resolution) params.value = { ...params.value, resolution: payload.resolution }
		// 画幅非空则带入
		if (payload.aspectRatio) params.value = { ...params.value, aspectRatio: payload.aspectRatio }
		// 参考图为数组则整体覆盖(图生图带入预置/用户选择的参考图)
		if (Array.isArray(payload.refImages)) refImages.value = payload.refImages.slice(0, 9)
		broughtIn.value = true
	}

	/**
	 * 消费带入标记(生成页预填后调用,避免重复预填)
	 */
	function consumeBroughtIn() {
		broughtIn.value = false
	}

	return {
		mode,
		params,
		prompt,
		refImages,
		tasks,
		results,
		resultPreviewUrls,
		resultTaskId,
		broughtIn,
		setMode,
		setParams,
		setPrompt,
		addRefImage,
		removeRefImage,
		reorderRefImages,
		addTask,
		setResults,
		restoreWorkItem,
		applyTemplate,
		consumeBroughtIn
	}
})

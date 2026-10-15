<template>
	<view class="create-content" :class="pagePerformanceClass" :style="pageVars">
		<scroll-view
			class="page-scroll"
			scroll-y
			:enhanced="scrollEnhanced"
			:bounces="scrollBounces"
			:show-scrollbar="scrollShowScrollbar"
			:lower-threshold="scrollLowerThreshold"
		>
			<view class="page-inner" :style="contentStyle">
				<view v-if="generationEnabled === false" class="feature-closed">
					<view class="feature-closed-icon">⌛</view>
					<text class="feature-closed-text">当前功能正在开发中</text>
				</view>
				<template v-else>
				<!-- AI 生成内容显著标识 -->
				<view class="ai-disclosure">
					<text class="ai-disclosure-label">AI生成</text>
					<text class="ai-disclosure-text">生成内容由人工智能生成，请注意辨别</text>
				</view>

				<ModelSelector v-if="userStore.isLoggedIn" label="出图模型" :models="imageModels" :model-value="generationStore.modelId" :disabled="submitting" :loading="modelsLoading" @update:model-value="selectImageModel" />
					<!-- 文生图 / 图生图 分段 -->
				<view class="mode-wrap">
					<AmSegmented v-model="mode" :options="modeOptions" @change="onModeChange" />
				</view>

				<!-- 渐变描边提示词输入卡 -->
				<view id="create-prompt-card" class="prompt-card">
						<textarea
							class="prompt-input"
							v-model="promptText"
							:maxlength="PROMPT_MAX_LENGTH"
						placeholder="描述你想要的图片，越具体越好…"
						placeholder-class="prompt-placeholder"
						:show-confirm-bar="false"
						:adjust-position="false"
						:cursor-spacing="24"
						:disable-default-padding="true"
					/>
					<!-- 卡内底部:比例 chip / 张数 chip + 圆形发送按钮 -->
					<view class="prompt-footer">
						<view class="param-chip" hover-class="am-tap-feedback" :hover-stay-time="70" @tap="openRatioSheet">
							<text class="param-chip-value">{{ aspectRatio }}</text>
							<text class="param-chip-arrow">⌄</text>
						</view>
						<view class="param-chip" hover-class="am-tap-feedback" :hover-stay-time="70" @tap="openCountSheet">
							<text class="param-chip-value">{{ count }}</text>
							<text class="param-chip-arrow">⌄</text>
						</view>
						<view
							v-if="resolutionOptions.length"
							class="param-chip"
							hover-class="am-tap-feedback"
							:hover-stay-time="70"
							@tap="openResolutionSheet"
						>
							<text class="param-chip-value">{{ resolution }}</text>
							<text class="param-chip-arrow">⌄</text>
						</view>
						<view class="footer-spacer" />
						<view
							class="send-btn"
							:class="{ 'send-btn--active': canSend }"
							hover-class="am-tap-feedback"
							:hover-stay-time="70"
							@tap="handleGenerate"
						>
							<text class="send-arrow">↑</text>
						</view>
					</view>
				</view>

				<!-- 输入卡外部右下:消耗提示(不伪造单价) -->
				<text class="consume-tip">{{ consumeTip }}</text>

				<!-- 图生图:参考图上传区 -->
				<RefImageUploader
					v-if="mode === 'img2img'"
					:images="refImages"
					:max="maxReferenceImages"
					@add="onRefAdd"
					@remove="onRefRemove"
					@reorder="onRefReorder"
					@uploading-change="referenceUploading = $event"
				/>

				<!-- 结果区 -->
					<ResultGrid
						:urls="results"
						:preview-urls="resultPreviewUrls"
						:mode="resultMode"
						:task-id="resultTaskId"
						@detail="openWorkDetail"
					/>

				<!-- 近期作品(7 天内出图,瀑布流);无作品时不渲染该区 -->
					<RecentWorks v-if="recentWorks.length" :works="recentWorks" @detail="openWorkDetail" />
				</template>
			</view>
		</scroll-view>

		<!-- 参数选择底部弹层(自绘 fixed 全屏遮罩:与页面同尺寸,即使被 overflow 裁剪也全屏可见,绕过组件在真机不弹的问题) -->
		<view class="param-sheet-mask" v-if="ratioSheetVisible || countSheetVisible || resolutionSheetVisible" @tap="closeParamSheets">
			<view class="param-sheet" @tap.stop>
				<view class="param-sheet-handle" />
				<text class="param-sheet-title">{{ paramSheetTitle }}</text>
				<view class="sheet-options">
					<template v-if="ratioSheetVisible">
						<view
							class="sheet-option"
							v-for="r in ratioOptions"
							:key="r"
							:class="{ 'sheet-option--active': aspectRatio === r }"
							hover-class="am-tap-feedback"
							:hover-stay-time="70"
							@tap="selectRatio(r)"
						>
							<text class="sheet-option-text">{{ r }}</text>
							<text class="sheet-option-check" v-if="aspectRatio === r">✓</text>
						</view>
					</template>
					<template v-else-if="countSheetVisible">
						<view
							class="sheet-option"
							v-for="c in countOptions"
							:key="c"
							:class="{ 'sheet-option--active': count === c }"
							hover-class="am-tap-feedback"
							:hover-stay-time="70"
							@tap="selectCount(c)"
						>
							<text class="sheet-option-text">{{ c }} 张</text>
							<text class="sheet-option-check" v-if="count === c">✓</text>
						</view>
					</template>
					<template v-else>
						<view
							class="sheet-option"
							v-for="r in resolutionOptions"
							:key="r"
							:class="{ 'sheet-option--active': resolution === r }"
							hover-class="am-tap-feedback"
							:hover-stay-time="70"
							@tap="selectResolution(r)"
						>
							<text class="sheet-option-text">{{ r }}</text>
							<text class="sheet-option-check" v-if="resolution === r">✓</text>
						</view>
					</template>
				</view>
			</view>
		</view>

		<!-- 进度浮层 -->
		<ProgressOverlay
			:visible="progressVisible"
			:percent="progressPercent"
			:status-bar-height="resolvedOverlayStatusBarHeight"
			:summary-text="progressSummary"
			:step-index="progressStep"
			@close="closeProgress"
		/>

		<WorkItemDetailOverlay
			:visible="detailVisible"
			:loading="detailLoading"
			:regenerating="regenerating"
			:detail="workDetail"
			:status-bar-height="resolvedOverlayStatusBarHeight"
			@close="closeWorkDetail"
			@edit="editWorkItem"
			@regenerate="regenerateWorkItemByOriginal"
		/>
	</view>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import ModelSelector from '@/components/common/ModelSelector.vue'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { useLoginGuard } from '@/composables/useLoginGuard.js'
import { useGenerationStore } from '@/store/generation.js'
import { useAccountStore } from '@/store/account.js'
import { useUserStore } from '@/store/user.js'
import {
	generateImage,
	getGenerationConfig,
	getGeneration,
	getGenerationOptions,
	getAvailableModels,
	getWorks,
	getWorkItemDetail,
	regenerateWorkItem,
} from '@/api/agent.js'
import { getBalance } from '@/api/account.js'
import AmSegmented from '@/components/am-ui/display/AmSegmented.vue'
import RefImageUploader from './components/RefImageUploader.vue'
import ProgressOverlay from './components/ProgressOverlay.vue'
import ResultGrid from './components/ResultGrid.vue'
import RecentWorks from './components/RecentWorks.vue'
import WorkItemDetailOverlay from './components/WorkItemDetailOverlay.vue'

const PROMPT_MAX_LENGTH = 32000

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ tabbar: true })
const { requireLogin } = useLoginGuard()

const generationStore = useGenerationStore()
const accountStore = useAccountStore()
const userStore = useUserStore()

const props = defineProps({
	statusBarHeight: { type: Number, default: 44 },
	overlayStatusBarHeight: { type: Number, default: undefined },
})
const emit = defineEmits(['navigation-change'])
const generationEnabled = ref(true)

// 顶部内容下移状态栏高度
const pageVars = computed(() => ({
	'--status-bar-height': `${props.statusBarHeight}px`,
}))
const resolvedOverlayStatusBarHeight = computed(() =>
	Number.isFinite(props.overlayStatusBarHeight)
		? props.overlayStatusBarHeight
		: props.statusBarHeight
)

// ====== 媒体 tab(视频置灰) ======
const mediaTabs = [
	{ label: '图片', value: 'image', disabled: false },
	{ label: '视频', value: 'video', disabled: true },
]
const mediaType = ref('image')

function emitNavigationState() {
	emit('navigation-change', {
		tabs: generationEnabled.value
			? mediaTabs.map((tab) => ({
				label: tab.label,
				key: tab.value,
				disabled: tab.disabled,
			}))
			: [],
		activeKey: mediaType.value,
	})
}

function onMediaTap(tab) {
	if (tab.disabled) {
		uni.showToast({ title: '视频功能即将上线', icon: 'none' })
		return
	}
	mediaType.value = tab.value
	emitNavigationState()
}

function selectNavigationTab(key) {
	const tab = mediaTabs.find((item) => String(item.value) === String(key))
	if (tab) onMediaTap(tab)
}

// ====== 文生图 / 图生图分段(绑定 store.mode) ======
const modeOptions = [
	{ label: '文生图', value: 'text2img' },
	{ label: '图生图', value: 'img2img' },
]
// 本地 ref 与 store.mode 双向同步:分段改本地 → 写 store
const mode = ref(generationStore.mode)
function onModeChange(val) {
	generationStore.setMode(val)
}
// store.mode 被外部(案例带入)改动时,同步回本地分段
watch(() => generationStore.mode, (val) => {
	if (mode.value !== val) mode.value = val
})

// ====== 提示词(绑定 store.prompt) ======
const promptText = computed({
	get: () => generationStore.prompt,
	set: (v) => generationStore.setPrompt(v),
})
// ====== 参数:比例 / 张数 / 分辨率(绑定 store.params) ======
const ratioOptions = ref(['1:1', '3:4', '9:16', '16:9'])
const imageModels = ref([]), modelsLoading = ref(false)
const selectedImageModel = computed(() => imageModels.value.find((m) => String(m.modelId) === String(generationStore.modelId)))
const maxReferenceImages = computed(() => selectedImageModel.value?.maxReferenceImages || 9)
function selectImageModel(id) { generationStore.setModelId(id); fetchGenerationOptions() }
const countOptions = [1, 2, 3, 4]
const resolutionOptions = ref([])
const resolutionBeanCosts = ref({})
const aspectRatio = computed(() => generationStore.params.aspectRatio)
const count = computed(() => generationStore.params.n)
const resolution = computed(() => generationStore.params.resolution || '')
const consumeTip = computed(() => {
	const unitPrice = Number(resolutionBeanCosts.value[resolution.value])
	if (Number.isFinite(unitPrice)) {
		return `本次生成 ${count.value} 张 · 预计消耗 ${unitPrice * count.value} 豆`
	}
	return `本次生成 ${count.value} 张 · 出图后按实际结算`
})

const ratioSheetVisible = ref(false)
const countSheetVisible = ref(false)
const resolutionSheetVisible = ref(false)
const paramSheetTitle = computed(() => {
	if (ratioSheetVisible.value) return '选择比例'
	if (countSheetVisible.value) return '选择张数'
	return '选择分辨率'
})
function openRatioSheet() {
	uni.hideKeyboard()
	// 互斥:打开比例时关掉张数
	countSheetVisible.value = false
	resolutionSheetVisible.value = false
	ratioSheetVisible.value = true
}
function openCountSheet() {
	uni.hideKeyboard()
	ratioSheetVisible.value = false
	resolutionSheetVisible.value = false
	countSheetVisible.value = true
}
function openResolutionSheet() {
	uni.hideKeyboard()
	ratioSheetVisible.value = false
	countSheetVisible.value = false
	resolutionSheetVisible.value = true
}
// 关闭参数弹层(点遮罩)
function closeParamSheets() {
	ratioSheetVisible.value = false
	countSheetVisible.value = false
	resolutionSheetVisible.value = false
}
function selectRatio(r) {
	generationStore.setParams({ aspectRatio: r })
	ratioSheetVisible.value = false
}
function selectCount(c) {
	generationStore.setParams({ n: c })
	countSheetVisible.value = false
}
function selectResolution(value) {
	generationStore.setParams({ resolution: value })
	resolutionSheetVisible.value = false
}

async function fetchGenerationOptions() {
	if (!userStore.isLoggedIn) return
	const selectedId = generationStore.modelId
	try {
		const data = await getGenerationOptions(selectedId)
		if (String(selectedId) !== String(generationStore.modelId)) return
		if (Array.isArray(data?.aspectRatios) && data.aspectRatios.length) {
			ratioOptions.value = data.aspectRatios
			if (!ratioOptions.value.includes(generationStore.params.aspectRatio)) generationStore.setParams({ aspectRatio: ratioOptions.value[0] })
		}
		const supported = Array.isArray(data && data.supportedResolutions)
			? data.supportedResolutions
				.map((item) => String(item || '').toUpperCase())
				.filter((item) => ['1K', '2K', '4K'].includes(item))
			: []
		resolutionOptions.value = [...new Set(supported)]
		const rawCosts = data && data.beanCosts && typeof data.beanCosts === 'object' ? data.beanCosts : {}
		resolutionBeanCosts.value = resolutionOptions.value.reduce((result, item) => {
			const value = Number(rawCosts[item])
			if (Number.isFinite(value)) result[item] = value
			return result
		}, {})
		if (!resolutionOptions.value.length) return
		const defaultResolution = String((data && data.defaultResolution) || '').toUpperCase()
		const current = String(generationStore.params.resolution || '').toUpperCase()
		const selected = resolutionOptions.value.includes(current)
			? current
			: (resolutionOptions.value.includes(defaultResolution) ? defaultResolution : resolutionOptions.value[0])
		generationStore.setParams({ resolution: selected })
	} catch (e) {
		// 选项拉取失败时不展示分辨率选择，提交继续使用后端默认值
		resolutionOptions.value = []
		resolutionBeanCosts.value = {}
	}
}

async function fetchModels() {
	if (!userStore.isLoggedIn) return
	modelsLoading.value = true
	try {
		const data = await getAvailableModels()
		imageModels.value = data?.imageModels || []
		if (!selectedImageModel.value) generationStore.setModelId((imageModels.value.find((m) => m.isDefault === 1 || m.isDefault === true) || imageModels.value[0])?.modelId || null)
	} catch { /* The server still validates any submitted model. */ }
	finally { modelsLoading.value = false }
}
async function fetchGenerationFeatureConfig() {
	try {
		const data = await getGenerationConfig()
		generationEnabled.value = data && data.generationEnabled !== false
		emitNavigationState()
		if (!generationEnabled.value) {
			clearPoll()
			progressVisible.value = false
			detailVisible.value = false
		}
	} catch (e) {
		// 配置接口异常时保持当前功能，真正提交仍受服务端开关保护。
	}
	return generationEnabled.value
}

// ====== 参考图(直接读写 store.refImages) ======
const refImages = computed(() => generationStore.refImages)
const referenceUploading = ref(false)
function onRefAdd(url) {
	generationStore.addRefImage(url)
}
function onRefRemove(index) {
	generationStore.removeRefImage(index)
}
function onRefReorder(order) {
	generationStore.reorderRefImages(order)
}

// ====== 发送态:空 prompt 不可发 ======
const canSend = computed(() => promptText.value.trim().length > 0 && !submitting.value && !referenceUploading.value)

// ====== 结果区 ======
const results = computed(() => generationStore.results)
const resultPreviewUrls = computed(() => generationStore.resultPreviewUrls)
const resultTaskId = computed(() => generationStore.resultTaskId)
// 结果区布局模式:固定为本次提交的模式(避免出图后切分段错乱)
const resultMode = ref('text2img')

// ====== 近期作品(7 天内出图,瀑布流)======
// 已展开的图项列表:[{ full }],full 为拼好 baseUrl 的完整地址
const recentWorks = ref([])

// 拉取近期作品:仅取生成页来源前 30 条 → 过滤 7 天内 → 展开 resultUrls 成图项
async function fetchRecentWorks() {
	let page
	try {
		page = await getWorks(1, 30, 'CREATE_PAGE')
	} catch (e) {
		// 拉取失败不影响主流程,保持当前列表
		return
	}
	const records = (page && page.records) || []
	const now = Date.now()
	const sevenDays = 7 * 24 * 60 * 60 * 1000
	const items = []
	for (const work of records) {
		// createTime 形如 "yyyy-MM-dd HH:mm:ss",replace 兼容 iOS 解析
		const ct = work && work.createTime
		if (!ct) continue
		const t = new Date(ct.replace(/-/g, '/')).getTime()
		if (isNaN(t) || now - t > sevenDays) continue
		const urls = (work && work.resultUrls) || []
		const previewUrls = (work && work.previewUrls) || []
		for (let index = 0; index < urls.length; index += 1) {
			const url = urls[index]
			if (!url) continue
			const previewUrl = previewUrls[index] || url
				items.push({ taskId: work.taskId, seq: index + 1, full: url, preview: previewUrl })
		}
	}
	recentWorks.value = items
}

// ====== 提交与轮询 ======
const submitting = ref(false)
const progressVisible = ref(false)
const progressPercent = ref(0)
const progressStep = ref(0)
const progressSummary = ref('')
let pollTimer = null

async function handleGenerate() {
	if (referenceUploading.value) {
		uni.showToast({ title: '参考图上传中，请稍候', icon: 'none' })
		return
	}
	if (!canSend.value) return
	if (!requireLogin()) return
	uni.hideKeyboard()

	const m = generationStore.mode
	if (!selectedImageModel.value) { uni.showToast({ title: '请选择可用的出图模型', icon: 'none' }); return }
	if (m === 'img2img' && !selectedImageModel.value.supportsReferenceImages) { uni.showToast({ title: '该模型不支持参考图，请切换模型', icon: 'none' }); return }
	if (m === 'img2img' && generationStore.refImages.length > maxReferenceImages.value) { uni.showToast({ title: `该模型最多支持 ${maxReferenceImages.value} 张参考图`, icon: 'none' }); return }
	// 图生图必须有参考图
	if (m === 'img2img' && generationStore.refImages.length === 0) {
		uni.showToast({ title: '请先上传参考图', icon: 'none' })
		return
	}

	submitting.value = true
	const body = {
		modelId: generationStore.modelId,
		prompt: promptText.value.trim(),
		aspectRatio: generationStore.params.aspectRatio,
		n: generationStore.params.n,
	}
	if (generationStore.params.resolution) {
		body.resolution = generationStore.params.resolution
	}
	if (m === 'img2img') {
		body.refImages = generationStore.refImages.slice()
	}
	try {
		body.requestId = generationStore.requestFor(body, userStore.userInfo?.id || userStore.userInfo?.userId || '')
		const res = await generateImage(body)
		const taskId = res && res.taskId
		if (!taskId) {
			uni.showToast({ title: '提交失败,请重试', icon: 'none' })
			return
		}
			generationStore.acknowledgeRequest(body.requestId)
			// 记录本次提交的模式,供结果区布局
			resultMode.value = m
			generationStore.setResults([], [], taskId)
			// 打开进度浮层并启动轮询
			openProgress(m, generationStore.params.aspectRatio, generationStore.params.n)
		startPoll(taskId)
	} catch (e) {
		handleGenerateError(e)
	} finally {
		submitting.value = false
	}
}

function handleGenerateError(error) {
	const detail = error && error.data && typeof error.data === 'object' ? error.data : error
	const errorCode = (detail && detail.errorCode) || (error && error.errorCode)
	const action = (detail && detail.action) || (error && error.action)
	if (errorCode !== 'INSUFFICIENT_BEANS' && action !== 'RECHARGE') return
	uni.showModal({
		title: '豆子不足',
		content: '当前豆子不足，充值后可返回继续生成。',
		confirmText: '去充值',
		success: (res) => {
			if (res.confirm) uni.navigateTo({ url: '/pages/bean/recharge/index' })
		},
	})
}

// 打开进度浮层(初始化展示态)
function openProgress(m, ratio, n) {
	progressVisible.value = true
	progressPercent.value = 6
	progressStep.value = 0
	progressSummary.value = `${ratio} × ${n} 张 · ${m === 'img2img' ? '图生图' : '文生图'}`
}

// 轮询出图结果(~2000ms)
function startPoll(taskId) {
	clearPoll()
	pollTimer = setInterval(async () => {
		let task
		try {
			task = await getGeneration(taskId)
		} catch (e) {
			// 单次轮询失败不中断,等待下次
			return
		}
		if (!task) return
		advanceProgress(task)
		if (Array.isArray(task.resultUrls) && task.resultUrls.length) {
			generationStore.setResults(task.resultUrls, task.previewUrls || [], taskId)
		}

		if (task.status === 'SUCCEEDED') {
			clearPoll()
			progressPercent.value = 100
			progressStep.value = 4
			generationStore.setResults(task.resultUrls || [], task.previewUrls || [], taskId)
			// 出图完成,关闭浮层并刷新余额
			setTimeout(() => {
				progressVisible.value = false
			}, 400)
			refreshBalance()
			// 出图成功后刷新近期作品(仅新增此调用,不改既有轮询逻辑)
			fetchRecentWorks()
		} else if (task.status === 'FAILED' || task.status === 'CANCELLED') {
			clearPoll()
			progressVisible.value = false
			uni.showToast({ title: task.errorMsg || '生成失败,豆子已退还', icon: 'none' })
			// 失败已退还,刷新余额
			refreshBalance()
		}
	}, 2000)
}

// 据 status/successCount 推进进度与步骤
function advanceProgress(task) {
	switch (task.status) {
		case 'CREATED':
			progressStep.value = 0
			progressPercent.value = Math.max(progressPercent.value, 10)
			break
		case 'QUEUED':
			progressStep.value = 1
			progressPercent.value = Math.max(progressPercent.value, 25)
			break
		case 'RUNNING': {
			progressStep.value = 2
			// 有成功张数时按比例推进,否则缓慢爬升
			const n = task.n || generationStore.params.n || 1
			const done = task.successCount || 0
			const base = 40
			const ratio = n > 0 ? done / n : 0
			const target = base + Math.round(ratio * 55)
			progressPercent.value = Math.max(progressPercent.value, Math.min(95, target || base))
			break
		}
		default:
			break
	}
}

function clearPoll() {
	if (pollTimer) {
		clearInterval(pollTimer)
		pollTimer = null
	}
}

function closeProgress() {
	// 收起浮层但不中断轮询(任务后台继续,出图后仍会写结果)
	progressVisible.value = false
}

// ====== 单张作品详情 / 编辑 / 原参数重新生成 ======
const detailVisible = ref(false)
const detailLoading = ref(false)
const regenerating = ref(false)
const workDetail = ref(null)

async function openWorkDetail({ taskId, seq }) {
	if (!taskId || !seq || detailLoading.value) return
	detailVisible.value = true
	detailLoading.value = true
	workDetail.value = null
	try {
		workDetail.value = await getWorkItemDetail(taskId, seq)
	} catch (e) {
		detailVisible.value = false
	} finally {
		detailLoading.value = false
	}
}

function closeWorkDetail() {
	if (regenerating.value) return
	detailVisible.value = false
	workDetail.value = null
}

function editWorkItem(detail) {
	if (!detail) return
	if (generationStore.restoreWorkItem(detail) === false) {
		uni.showToast({ title: detail.replayUnavailableReason || '请重新选择参考素材后生成', icon: 'none' })
		return
	}
	mode.value = generationStore.mode
	closeWorkDetail()
	uni.showToast({ title: '已恢复原参数，可修改后发送', icon: 'none' })
}

async function regenerateWorkItemByOriginal(detail) {
	if (!detail || regenerating.value || !requireLogin()) return
	if (detail.replaySupported === false) {
		uni.showToast({ title: detail.replayUnavailableReason || '请重新选择参考素材后生成', icon: 'none' })
		return
	}
	regenerating.value = true
	try {
		const response = await regenerateWorkItem(detail.taskId, detail.seq)
		const taskId = response && response.taskId
		if (!taskId) {
			uni.showToast({ title: '提交失败,请重试', icon: 'none' })
			return
		}
		resultMode.value = detail.mode === 'img2img' ? 'img2img' : 'text2img'
		generationStore.setResults([], [], taskId)
		detailVisible.value = false
		workDetail.value = null
		openProgress(resultMode.value, detail.aspectRatio || '默认比例', 1)
		startPoll(taskId)
	} catch (e) {
		handleGenerateError(e)
	} finally {
		regenerating.value = false
	}
}

// 刷新豆子余额
async function refreshBalance() {
	try {
		const data = await getBalance()
		// 兼容后端返回 number 或 { balance }
		if (typeof data === 'number') {
			accountStore.setBalance(data)
		} else if (data && data.balance != null) {
			accountStore.setBalance(data.balance)
		}
	} catch (e) {
		// 余额刷新失败不影响主流程
	}
}

// ====== 案例带入预填 ======
watch(
	() => generationStore.broughtIn,
	(v) => {
		if (v) {
			// store 已写好 prompt/mode/n,此处仅同步本地分段并消费标记
			mode.value = generationStore.mode
			generationStore.consumeBroughtIn()
		}
	},
	{ immediate: true }
)

// 进入页面拉取一次近期作品
async function reloadCreatePage() {
	const enabled = await fetchGenerationFeatureConfig()
	if (!enabled) return
	fetchRecentWorks()
	await fetchModels()
	fetchGenerationOptions()
}

onMounted(() => {
	emitNavigationState()
	void reloadCreatePage()
})

onUnmounted(() => {
	clearPoll()
})

// 供宿主页在切回生成 tab 时重拉(首次加载失败/保持最新);组件 v-show 保活 onMounted 不重跑
defineExpose({
	reloadRecentWorks: reloadCreatePage,
	selectNavigationTab,
})
</script>

<style lang="scss" scoped>
.create-content {
	height: 100%;
	background: #f6f7fb;
	position: relative;
	overflow: hidden;
}

.page-scroll {
	height: 100%;
	position: relative;
	z-index: 2;
	overscroll-behavior: none;
}

.page-inner {
	padding: calc(var(--status-bar-height) + 16rpx) 40rpx 0;
}

.feature-closed {
	min-height: 70vh;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 24rpx;
}

.feature-closed-icon {
	width: 112rpx;
	height: 112rpx;
	border-radius: 32rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #eaf2ff;
	font-size: 48rpx;
}

.feature-closed-text {
	font-size: 30rpx;
	font-weight: 600;
	color: #64748b;
}

/* AI 生成内容显著标识 */
.ai-disclosure {
	display: flex;
	align-items: center;
	margin-top: 24rpx;
	padding: 16rpx 20rpx;
	border: 2rpx solid rgba(79, 132, 234, 0.28);
	border-radius: 20rpx;
	background: #eaf2ff;
}

.ai-disclosure-label {
	flex-shrink: 0;
	padding: 6rpx 14rpx;
	border-radius: 12rpx;
	background: #4f84ea;
	font-size: 23rpx;
	font-weight: 700;
	color: #ffffff;
}

.ai-disclosure-text {
	margin-left: 14rpx;
	font-size: 24rpx;
	line-height: 1.45;
	color: #334155;
}

/* 文生图 / 图生图分段 */
.mode-wrap {
	margin-top: 28rpx;
}

/* 渐变描边提示词输入卡 */
.prompt-card {
	margin-top: 28rpx;
	padding: 28rpx 28rpx 22rpx;
	box-sizing: border-box;
	border-radius: 36rpx;
	border: 3rpx solid transparent;
	background:
		linear-gradient(#ffffff, #ffffff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.prompt-input {
	width: 100%;
	height: 180rpx;
	min-height: 180rpx;
	max-height: 180rpx;
	box-sizing: border-box;
	font-size: 30rpx;
	color: #1e293b;
	line-height: 1.6;
}

.prompt-placeholder {
	color: #94a3b8;
	font-size: 30rpx;
}

.prompt-footer {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-top: 16rpx;
}

.param-chip {
	display: flex;
	align-items: center;
	gap: 8rpx;
	height: 60rpx;
	padding: 0 22rpx;
	border-radius: 30rpx;
	background: #eef3fb;
	transition: opacity 0.15s ease;
}

.param-chip:active {
	opacity: 0.85;
}

.param-chip-value {
	font-size: 26rpx;
	font-weight: 600;
	color: #15161b;
	line-height: 1;
}

.param-chip-arrow {
	font-size: 24rpx;
	color: #94a3b8;
	line-height: 1;
}

.footer-spacer {
	flex: 1;
}

.send-btn {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background: #d7dbe3;
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
	transition: opacity 0.15s ease, transform 0.15s ease;
}

.send-btn--active {
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	box-shadow: 0 12rpx 32rpx rgba(96, 140, 224, 0.3);
}

.send-btn--active:active {
	opacity: 0.92;
	transform: scale(0.96);
}

.send-arrow {
	font-size: 40rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 1;
}

/* 消耗提示 */
.consume-tip {
	display: block;
	margin: 16rpx 4rpx 0;
	text-align: right;
	font-size: 22rpx;
	color: #94a3b8;
	line-height: 1.4;
}

/* 参数选择自绘底部弹层 */
.param-sheet-mask {
	position: fixed;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
	z-index: 9999;
	background: rgba(15, 23, 42, 0.42);
	display: flex;
	align-items: flex-end;
	animation: create-mask-in 0.16s ease-out;
}

.param-sheet {
	width: 100%;
	box-sizing: border-box;
	padding: 18rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
	background: #ffffff;
	border-radius: 32rpx 32rpx 0 0;
	box-shadow: 0 -12rpx 34rpx rgba(15, 23, 42, 0.12);
	animation: create-sheet-up 0.2s ease-out;
}

@keyframes create-mask-in {
	from { opacity: 0; }
	to { opacity: 1; }
}

@keyframes create-sheet-up {
	from { transform: translateY(28rpx); opacity: 0.88; }
	to { transform: translateY(0); opacity: 1; }
}

.param-sheet-handle {
	width: 72rpx;
	height: 8rpx;
	border-radius: 999rpx;
	background: #e3e8f0;
	margin: 0 auto 20rpx;
}

.param-sheet-title {
	display: block;
	font-size: 30rpx;
	font-weight: 700;
	color: #0f172a;
	line-height: 1.4;
}

/* 底部弹窗选项 */
.sheet-options {
	padding: 8rpx 0 12rpx;
}

.sheet-option {
	display: flex;
	align-items: center;
	justify-content: space-between;
	height: 96rpx;
	padding: 0 12rpx;
	border-bottom: 2rpx solid #edeef2;
}

.sheet-option:last-child {
	border-bottom: none;
}

.sheet-option-text {
	font-size: 30rpx;
	color: #1e293b;
	line-height: 1;
}

.sheet-option--active .sheet-option-text {
	color: #4f84ea;
	font-weight: 600;
}

.sheet-option-check {
	font-size: 30rpx;
	color: #4f84ea;
	line-height: 1;
}

/* 性能轻量模式:去阴影 */
.am-performance-light .send-btn--active {
	box-shadow: none;
}

.am-performance-light .prompt-card,
.am-motion-none .prompt-card {
	transition: none;
}

.am-motion-none .param-sheet-mask,
.am-motion-none .param-sheet {
	animation: none;
}
</style>

<template>
	<!-- 使用模板弹窗:底部大弹层(可滚),区分文生图/图生图 -->
	<view v-if="visible" class="ut-mask" @tap="onClose">
		<view class="ut-sheet" @tap.stop>
			<!-- 顶部:把手 + 标题 + 关闭 -->
			<view class="ut-header">
				<view class="ut-handle" />
				<text class="ut-title">使用模板</text>
				<view class="ut-close" @tap="onClose">
					<text class="ut-close-text">×</text>
				</view>
			</view>

			<!-- 可滚动内容区 -->
			<scroll-view class="ut-scroll" scroll-y :show-scrollbar="false">
				<view class="ut-body">
					<!-- 提示词(可编辑) -->
					<view class="ut-block">
						<text class="ut-label">提示词</text>
						<view class="ut-prompt-card">
							<textarea
								class="ut-prompt-input"
								v-model="promptText"
								:maxlength="500"
								placeholder="描述你想要的画面，越具体越好…"
								placeholder-class="ut-prompt-placeholder"
								:show-confirm-bar="false"
								:adjust-position="false"
							/>
						</view>
					</view>

					<view class="ut-model-controls">
						<ModelSelector label="出图模型" compact :models="imageModels" :model-value="selectedModelId"
							:loading="modelsLoading" :disabled="confirming" @update:model-value="selectImageModel" />
						<ImageResolutionSelector :options="resolutionOptions" v-model="resolution"
							:loading="modelsLoading" :disabled="confirming || !selectedModel" />
					</view>
					<view v-if="modelError" class="ut-options-note">
						<text>{{ modelError }}</text>
						<text class="ut-options-retry" @tap="retryModels">{{ userStore.isLoggedIn ? '重新加载' : '去登录' }}</text>
					</view>

					<!-- 画幅 -->
					<view class="ut-block">
						<text class="ut-label">画幅</text>
						<view class="ut-chips">
							<view
								class="ut-chip"
								v-for="r in ratioOptions"
								:key="r"
								:class="{ 'ut-chip--active': aspectRatio === r }"
								@tap="aspectRatio = r"
							>
								<text class="ut-chip-text">{{ r }}</text>
							</view>
						</view>
					</view>

					<!-- 张数 -->
					<view class="ut-block">
						<text class="ut-label">生成张数（最多9张）</text>
						<view class="ut-chips ut-chips--counts">
							<view
								class="ut-chip ut-chip--count"
								v-for="c in countOptions"
								:key="c"
								:class="{ 'ut-chip--active': count === c }"
								@tap="count = c"
							>
								<text class="ut-chip-text">{{ c }}</text>
							</view>
						</view>
					</view>

					<!-- 模板与生成页共用原图上传和顺序保护，模板保留最多4张参考图。 -->
					<RefImageUploader
						v-if="isImg2Img"
						:images="refImages"
						:max="4"
						@add="refImages.push($event)"
						@remove="removeRef"
						@reorder="reorderRefs"
						@uploading-change="uploading = $event"
					/>
				</view>
			</scroll-view>

			<!-- 底部:确认使用 -->
			<view class="ut-cta">
				<text class="ut-cost">{{ consumeTip }}</text>
				<view class="ut-cta-btn" :class="{ 'ut-cta-btn--disabled': confirming || modelsLoading || !selectedModel }" @tap="onConfirm">
					<text class="ut-cta-text">{{ confirming ? '正在带入…' : '确认使用' }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, ref, watch, onUnmounted } from 'vue'
import RefImageUploader from '@/pages/create/components/RefImageUploader.vue'
import ModelSelector from '@/components/common/ModelSelector.vue'
import ImageResolutionSelector from '@/components/common/ImageResolutionSelector.vue'
import { useTemplate } from '@/api/template.js'
import { getAvailableModels } from '@/api/agent.js'
import { useGenerationStore } from '@/store/generation.js'
import { useTabbarStore } from '@/store/tabbar.js'
import { useUserStore } from '@/store/user.js'
import { useLoginGuard } from '@/composables/useLoginGuard.js'
import { imageResolutionOptions, chooseImageResolution } from '@/utils/image-resolution.js'

const props = defineProps({
	// 弹窗可见
	visible: { type: Boolean, default: false },
	// 详情 TemplateVO(含 prompt/params/referenceImages 等)
	template: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:visible', 'confirm'])

const generationStore = useGenerationStore()
const tabbarStore = useTabbarStore()
const userStore = useUserStore()
const { requireLogin } = useLoginGuard()

// 画幅与价格使用后台能力，张数1~9。
const fallbackRatios = ['1:1', '3:4', '4:3', '16:9', '9:16']
const countOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9]
const imageModels = ref([])
const selectedModelId = ref(null)
const selectedModel = computed(() => imageModels.value.find((model) => String(model.modelId) === String(selectedModelId.value)))
const resolution = ref('')
const resolutionOptions = computed(() => imageResolutionOptions(selectedModel.value))
const ratioOptions = computed(() => selectedModel.value?.aspectRatios?.length ? selectedModel.value.aspectRatios : fallbackRatios)
const modelsLoading = ref(false)
const modelError = ref('')
const confirming = ref(false)
let modalVersion = 0
let catalogVersion = 0

// ====== 本地编辑态:弹窗打开时按 template 初始化 ======
const promptText = ref('')
const aspectRatio = ref('3:4')
const count = ref(1)
// 模板模式(text2img / img2img),决定是否显示参考图区
const mode = ref('text2img')
const isImg2Img = computed(() => mode.value === 'img2img')
// 参考图相对路径列表(/profile/...)
const refImages = ref([])
// 上传中标记(防重复点)
const uploading = ref(false)
const consumeTip = computed(() => {
	if (modelsLoading.value) return '正在加载档位与价格…'
	const option = resolutionOptions.value.find((item) => item.value === resolution.value)
	return option?.beans != null
		? `本次生成 ${count.value} 张 · 预计消耗 ${option.beans * count.value} 豆`
		: '请选择可用的档位与清晰度'
})

function normalizeCount(value) {
	const n = Number(value)
	return Number.isInteger(n) ? Math.min(9, Math.max(1, n)) : 1
}

function selectImageModel(id) {
	if (confirming.value || modelsLoading.value) return
	const model = imageModels.value.find((item) => String(item.modelId) === String(id))
	if (!model) return
	selectedModelId.value = model.modelId
	resolution.value = chooseImageResolution(model, resolution.value)
	if (!ratioOptions.value.includes(aspectRatio.value)) aspectRatio.value = ratioOptions.value[0]
}

async function loadModels() {
	const version = ++catalogVersion
	const opening = modalVersion
	imageModels.value = []
	modelsLoading.value = false
	modelError.value = ''
	if (!userStore.isLoggedIn) { modelError.value = '登录后可选择出图模型和清晰度'; return }
	modelsLoading.value = true
	try {
		const data = await getAvailableModels()
		if (!props.visible || opening !== modalVersion || version !== catalogVersion) return
		imageModels.value = Array.isArray(data?.imageModels) ? data.imageModels : []
		const chosen = imageModels.value.find((item) => String(item.modelId) === String(selectedModelId.value))
			|| imageModels.value.find((item) => item.isDefault === true || item.isDefault === 1) || imageModels.value[0]
		selectedModelId.value = chosen?.modelId ?? null
		resolution.value = chooseImageResolution(chosen, resolution.value)
		if (!ratioOptions.value.includes(aspectRatio.value)) aspectRatio.value = ratioOptions.value[0]
		if (!chosen) modelError.value = '暂无可用出图档位，请稍后重试'
	} catch {
		if (opening === modalVersion && version === catalogVersion) modelError.value = '档位加载失败，请重新加载'
	} finally {
		if (opening === modalVersion && version === catalogVersion) modelsLoading.value = false
	}
}

function retryModels() {
	if (!requireLogin() || confirming.value || modelsLoading.value) return
	void loadModels()
}

// 安全解析 params(JSON 串),失败返回空对象
function parseParams(raw) {
	if (!raw) return {}
	try {
		const obj = JSON.parse(raw)
		return obj && typeof obj === 'object' ? obj : {}
	} catch (e) {
		return {}
	}
}

// 安全解析 referenceImages(JSON 数组串),失败返回空数组
function parseRefImages(raw) {
	if (!raw) return []
	try {
		const arr = JSON.parse(raw)
		return Array.isArray(arr) ? arr.filter(Boolean) : []
	} catch (e) {
		return []
	}
}

// 弹窗打开时(visible 转 true)按当前 template 重置本地编辑态
watch(
	() => [props.visible, props.template],
	([v]) => {
		modalVersion++
		catalogVersion++
		confirming.value = false
		if (!v) return
		const t = props.template || {}
		const params = parseParams(t.params)
		promptText.value = t.prompt || ''
		aspectRatio.value = params.aspectRatio || '3:4'
		count.value = normalizeCount(params.count ?? params.n ?? 1)
		selectedModelId.value = params.modelId ?? generationStore.modelId ?? null
		resolution.value = String(params.resolution || generationStore.params.resolution || '').toUpperCase()
		mode.value = params.mode === 'img2img' ? 'img2img' : 'text2img'
		// 图生图预置参考图带入,文生图清空
		refImages.value = mode.value === 'img2img' ? parseRefImages(t.referenceImages) : []
		void loadModels()
	},
	{ immediate: true }
)

watch(() => userStore.isLoggedIn, () => { if (props.visible) void loadModels() })
onUnmounted(() => { modalVersion++; catalogVersion++ })

function removeRef(index) {
	refImages.value.splice(index, 1)
}

function reorderRefs(order) {
	if (!Array.isArray(order) || order.length !== refImages.value.length || new Set(order).size !== order.length) return
	if (order.some((index) => !Number.isInteger(index) || index < 0 || index >= refImages.value.length)) return
	const current = refImages.value
	refImages.value = order.map((index) => current[index])
}

// 关闭弹窗(点遮罩 / 关闭按钮)
function onClose() {
	modalVersion++
	catalogVersion++
	emit('update:visible', false)
}

/**
 * 确认使用:计数 +1(失败不阻断)→ 带入生成 store → 切到生成页 → 关闭弹窗
 * 图生图把参考图 url 数组一并带入,文生图不带参考图
 */
async function onConfirm() {
	if (uploading.value) {
		uni.showToast({ title: '参考图上传中，请稍候', icon: 'none' })
		return
	}
	if (confirming.value || !props.visible || !requireLogin()) return
	if (modelsLoading.value || !selectedModel.value) {
		uni.showToast({ title: modelsLoading.value ? '档位加载中，请稍候' : '请先加载可用出图档位', icon: 'none' })
		return
	}
	const model = selectedModel.value
	const option = resolutionOptions.value.find((item) => item.value === resolution.value)
	if (!option || option.beans == null) { uni.showToast({ title: '该清晰度暂不可用，请重新选择', icon: 'none' }); return }
	if (!promptText.value.trim()) { uni.showToast({ title: '请填写提示词', icon: 'none' }); return }
	if (isImg2Img.value && !model.supportsReferenceImages) { uni.showToast({ title: '该档位不支持参考图，请切换档位', icon: 'none' }); return }
	if (isImg2Img.value && refImages.value.length > (model.maxReferenceImages || 9)) {
		uni.showToast({ title: `该档位最多支持 ${model.maxReferenceImages || 9} 张参考图`, icon: 'none' }); return
	}
	const t = props.template || {}
	// 图生图必须有参考图
	if (isImg2Img.value && refImages.value.length === 0) {
		uni.showToast({ title: '请先上传参考图', icon: 'none' })
		return
	}
	const payload = {
		prompt: promptText.value,
		mode: mode.value,
		modelId: model.modelId,
		resolution: resolution.value,
		n: normalizeCount(count.value),
		aspectRatio: aspectRatio.value,
		refImages: isImg2Img.value ? refImages.value.slice() : [],
	}
	const opening = modalVersion
	confirming.value = true
	try {
		// 使用次数统计失败不影响参数带入，不在此处提交出图或扣费。
		if (t.templateId != null) { try { await useTemplate(t.templateId) } catch {} }
		if (!props.visible || opening !== modalVersion || !userStore.isLoggedIn) return
		generationStore.applyTemplate(payload)
		emit('confirm')
		emit('update:visible', false)
		tabbarStore.switchTo('create')
	} finally {
		if (opening === modalVersion) confirming.value = false
	}
}
</script>

<style lang="scss" scoped>
/* 遮罩 */
.ut-mask {
	position: fixed;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
	/* 高于底部导航(z-index:100)与案例详情浮层(200),否则底部「确认使用」被遮挡 */
	z-index: 300;
	background: rgba(15, 23, 42, 0.42);
	display: flex;
	align-items: flex-end;
}

/* 底部大弹层:flex 列,中间内容区滚动 */
.ut-sheet {
	width: 100%;
	max-height: 86vh;
	box-sizing: border-box;
	background: #f6f7fb;
	border-radius: 32rpx 32rpx 0 0;
	box-shadow: 0 -12rpx 34rpx rgba(15, 23, 42, 0.12);
	display: flex;
	flex-direction: column;
}

/* 顶部标题区 */
.ut-header {
	position: relative;
	flex-shrink: 0;
	padding: 18rpx 32rpx 12rpx;
}

.ut-handle {
	width: 72rpx;
	height: 8rpx;
	border-radius: 999rpx;
	background: #e3e8f0;
	margin: 0 auto 16rpx;
}

.ut-title {
	display: block;
	text-align: center;
	font-size: 32rpx;
	font-weight: 700;
	color: #0f172a;
	line-height: 1.4;
}

.ut-close {
	position: absolute;
	right: 24rpx;
	top: 24rpx;
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background: #edeef2;
	display: flex;
	align-items: center;
	justify-content: center;
}

.ut-close-text {
	font-size: 40rpx;
	color: #94a3b8;
	line-height: 1;
}

/* 内容滚动区:flex:1 + min-height:0 让其可收缩并内部滚动 */
.ut-scroll {
	flex: 1;
	min-height: 0;
}

.ut-body {
	padding: 12rpx 32rpx 16rpx;
}

.ut-block {
	margin-top: 28rpx;
}

.ut-block:first-child {
	margin-top: 8rpx;
}

.ut-label {
	display: block;
	font-size: 26rpx;
	font-weight: 600;
	color: #64748b;
	line-height: 1.2;
	margin-bottom: 16rpx;
}

/* 提示词卡(渐变描边) */
.ut-prompt-card {
	padding: 24rpx 24rpx;
	box-sizing: border-box;
	border-radius: 28rpx;
	border: 3rpx solid transparent;
	background:
		linear-gradient(#ffffff, #ffffff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
}

.ut-prompt-input {
	width: 100%;
	/* 固定高度(不用 auto-height):避免弹窗 flex+scroll 布局首屏因高度变化而错位抖动;内容超出时框内可滚 */
	height: 300rpx;
	font-size: 28rpx;
	color: #1e293b;
	line-height: 1.6;
}

.ut-prompt-placeholder {
	color: #94a3b8;
	font-size: 28rpx;
}

/* chip 选项组 */
.ut-chips {
	display: flex;
	flex-wrap: wrap;
	gap: 16rpx;
}

.ut-chips--counts { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14rpx; }
.ut-model-controls { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin-top: 28rpx; }
.ut-options-note { display: flex; justify-content: space-between; gap: 16rpx; margin-top: 12rpx; color: #64748b; font-size: 24rpx; }
.ut-options-retry { color: #4f76ad; flex-shrink: 0; }
.ut-cost { display: block; text-align: center; font-size: 24rpx; color: #64748b; margin-bottom: 16rpx; }
.ut-cta-btn--disabled { opacity: 0.55; }

.ut-chip {
	min-width: 96rpx;
	height: 64rpx;
	padding: 0 28rpx;
	box-sizing: border-box;
	border-radius: 32rpx;
	background: #ffffff;
	border: 2rpx solid #edeef2;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: opacity 0.15s ease;
}

.ut-chip--count {
	min-width: 64rpx;
	padding: 0 20rpx;
}

.ut-chip:active {
	opacity: 0.85;
}

.ut-chip--active {
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	border-color: transparent;
}

.ut-chip-text {
	font-size: 26rpx;
	font-weight: 600;
	color: #475569;
	line-height: 1;
}

.ut-chip--active .ut-chip-text {
	color: #ffffff;
}

/* 底部 CTA */
.ut-cta {
	flex-shrink: 0;
	padding: 16rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
	background: #f6f7fb;
}

.ut-cta-btn {
	width: 100%;
	height: 96rpx;
	border-radius: 30rpx;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 12rpx 32rpx rgba(96, 140, 224, 0.3);
}

.ut-cta-btn:active {
	opacity: 0.92;
}

.ut-cta-text {
	font-size: 32rpx;
	font-weight: 700;
	color: #ffffff;
}

/* 性能降级:去阴影 */
.am-performance-light .ut-cta-btn {
	box-shadow: none;
}
</style>

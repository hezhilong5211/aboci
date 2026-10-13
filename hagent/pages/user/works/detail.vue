<template>
	<view class="detail-page" :class="pagePerformanceClass">
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
				<text class="nav-title">作品详情</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<scroll-view class="content" scroll-y>
			<view class="content-inner" :style="{ paddingTop: (statusBarHeight + 44) + 'px' }">
				<view class="loading-state" v-if="loading"><text>正在加载作品...</text></view>
				<view class="loading-state" v-else-if="!detail"><text>{{ errorText || '作品不存在' }}</text></view>

				<template v-else>
					<view class="overview-card">
						<view class="overview-top">
							<text class="source-tag" :class="{ agent: detail.sourceType === 'AGENT' }">
								{{ sourceLabel(detail.sourceType) }}
							</text>
							<text class="overview-time">{{ detail.createTime }}</text>
						</view>
						<text class="overview-title">本批次共 {{ detail.plannedCount }} 张</text>
						<view class="overview-counts">
							<text class="success-count">成功 {{ detail.successCount }}</text>
							<text class="failed-count" v-if="detail.failedCount">失败 {{ detail.failedCount }}</text>
							<text class="bean-count">实扣 {{ detail.totalBeanCost || 0 }} 豆</text>
						</view>
					</view>

					<view class="section-card album-section">
						<view class="section-heading">
							<text class="section-title">本批次相册</text>
							<text class="section-tip">点击图片查看对应详情</text>
						</view>
						<view class="album-grid">
							<view
								class="album-item"
								:class="{ selected: selectedSeq === item.seq, failed: item.status === 'FAILED' }"
								v-for="item in detail.items"
								:key="item.seq"
								@tap="selectItem(item.seq)"
							>
								<CachedGeneratedImage
									v-if="item.imageUrl"
									class="album-image"
									:src="resolveUrl(item.previewUrl || item.imageUrl)"
									:fallback-src="resolveUrl(item.imageUrl)"
									:user-id="currentUserId"
									cache-type="preview"
									mode="aspectFill"
									lazy-load
								/>
								<view class="failed-placeholder" v-else>
									<text class="failed-icon">!</text>
									<text>{{ item.status === 'GENERATING' ? '生成中' : '生成失败' }}</text>
								</view>
								<text class="image-seq">图{{ item.seq }}</text>
								<text v-if="item.imageUrl" class="ai-image-label">AI生成</text>
							</view>
						</view>
					</view>

					<view class="section-card item-detail" v-if="selectedItem">
						<view class="item-heading">
							<text class="item-title">图{{ selectedItem.seq }} 详情</text>
							<text class="item-status" :class="selectedItem.status.toLowerCase()">
								{{ statusLabel(selectedItem.status) }}
							</text>
						</view>

						<view class="selected-preview" v-if="selectedItem.imageUrl" @tap="previewItem(selectedItem)">
							<CachedGeneratedImage
								class="selected-image"
								:src="resolveUrl(selectedItem.previewUrl || selectedItem.imageUrl)"
								:fallback-src="resolveUrl(selectedItem.imageUrl)"
								:user-id="currentUserId"
								cache-type="preview"
								mode="widthFix"
							/>
							<text class="preview-hint">点击查看原图</text>
							<text class="ai-image-label ai-image-label--large">AI生成</text>
						</view>

						<view class="prompt-block" v-if="detail.sourceType === 'AGENT' && selectedItem.executionTarget">
							<text class="block-label">本张执行目标</text>
							<text
								class="prompt-text am-selectable-text"
								:class="{ 'prompt-text--collapsed': isPromptCollapsed('executionTarget', selectedItem.executionTarget) }"
								user-select
							>{{ selectedItem.executionTarget }}</text>
							<text
								class="prompt-toggle"
								v-if="isPromptCollapsible(selectedItem.executionTarget)"
								@tap.stop="togglePrompt('executionTarget')"
							>{{ isPromptExpanded('executionTarget') ? '收起' : '展开全部' }}</text>
						</view>
						<view class="prompt-block" v-if="detail.sourceType === 'AGENT' && selectedItem.userPrompt">
							<text class="block-label">原始用户要求</text>
							<text
								class="prompt-text am-selectable-text"
								:class="{ 'prompt-text--collapsed': isPromptCollapsed('userPrompt', selectedItem.userPrompt) }"
								user-select
							>{{ selectedItem.userPrompt }}</text>
							<text
								class="prompt-toggle"
								v-if="isPromptCollapsible(selectedItem.userPrompt)"
								@tap.stop="togglePrompt('userPrompt')"
							>{{ isPromptExpanded('userPrompt') ? '收起' : '展开全部' }}</text>
						</view>
						<view class="prompt-block">
							<text class="block-label">
								{{ detail.sourceType === 'AGENT' ? '实际发送给图片模型的完整提示词' : '生成提示词' }}
							</text>
							<text
								class="prompt-text full-prompt am-selectable-text"
								:class="{ 'prompt-text--collapsed': isPromptCollapsed('fullPrompt', selectedItem.fullPrompt) }"
								user-select
							>{{ selectedItem.fullPrompt || '无' }}</text>
							<text
								class="prompt-toggle"
								v-if="isPromptCollapsible(selectedItem.fullPrompt)"
								@tap.stop="togglePrompt('fullPrompt')"
							>{{ isPromptExpanded('fullPrompt') ? '收起' : '展开全部' }}</text>
						</view>

						<view class="reference-section">
							<view class="section-heading inline-heading">
								<text class="section-title">实际参考图</text>
								<text class="section-tip">{{ selectedItem.references.length ? `${selectedItem.references.length} 张` : '未使用' }}</text>
							</view>
							<view class="reference-grid" v-if="selectedItem.references.length">
								<view class="reference-item" v-for="reference in selectedItem.references" :key="reference.index">
									<view class="reference-image-wrap" @tap="previewReference(reference)">
										<CachedGeneratedImage
											v-if="reference.url"
											class="reference-image"
											:src="referencePreviewUrl(reference.url)"
											:fallback-src="resolveUrl(reference.url)"
											:user-id="currentUserId"
											cache-type="preview"
											mode="aspectFill"
											lazy-load
										/>
										<view class="missing-reference" v-else><text>来源图片失败</text></view>
										<text class="reference-seq">图{{ reference.index }}</text>
									</view>
									<text class="reference-source">{{ referenceLabel(reference) }}</text>
								</view>
							</view>
						</view>

						<view class="parameter-grid">
							<view class="parameter-item">
								<text class="parameter-label">生成模式</text>
								<text class="parameter-value">{{ selectedItem.mode === 'img2img' ? '图生图' : '文生图' }}</text>
							</view>
							<view class="parameter-item">
								<text class="parameter-label">分辨率</text>
								<text class="parameter-value">{{ detail.resolution || '默认' }}</text>
							</view>
							<view class="parameter-item">
								<text class="parameter-label">比例</text>
								<text class="parameter-value">{{ detail.aspectRatio || '默认' }}</text>
							</view>
							<view class="parameter-item">
								<text class="parameter-label">单张实扣</text>
								<text class="parameter-value">{{ selectedItem.beanCost || 0 }} 豆</text>
							</view>
						</view>
					</view>
				</template>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import config from '@/utils/config.js'
import { getWorkBatchDetail } from '@/api/agent.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'
import { useUserStore } from '@/store/user.js'
import CachedGeneratedImage from '@/components/common/CachedGeneratedImage.vue'
import { previewGeneratedImages } from '@/utils/generated-image-cache.js'

const { pagePerformanceClass } = usePerformanceMode()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo && userStore.userInfo.userId)
const statusBarHeight = ref(44)
const detail = ref(null)
const selectedSeq = ref(1)
const loading = ref(true)
const errorText = ref('')
const expandedPrompts = ref({})

const selectedItem = computed(() => {
	if (!detail.value || !Array.isArray(detail.value.items)) return null
	return detail.value.items.find((item) => item.seq === selectedSeq.value) || detail.value.items[0] || null
})

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

function resolveUrl(url) {
	if (!url) return ''
	return /^https?:\/\//.test(url) ? url : config.baseUrl + url
}

function referencePreviewUrl(url) {
	if (!url) return ''
	const preview = url.includes('/profile/generated/') && url.endsWith('.png')
		? url.slice(0, -4) + '.preview.jpg'
		: url
	return resolveUrl(preview)
}

function sourceLabel(sourceType) {
	return sourceType === 'AGENT' ? 'Agent 对话' : '生成页面'
}

function statusLabel(status) {
	if (status === 'SUCCEEDED') return '生成成功'
	if (status === 'GENERATING') return '生成中'
	return '生成失败'
}

function referenceLabel(reference) {
	if (reference.sourceType === 'GENERATED' && reference.sourceSeq) {
		return `来自本批次图${reference.sourceSeq}`
	}
	return reference.sourceType === 'UPLOADED' ? '用户上传参考图' : '原任务参考图'
}

function selectItem(seq) {
	selectedSeq.value = seq
}

function promptStateKey(type) {
	return `${selectedSeq.value}:${type}`
}

function isPromptCollapsible(text) {
	if (!text) return false
	return text.length > 120 || text.split(/\r?\n/).length > 6
}

function isPromptExpanded(type) {
	return !!expandedPrompts.value[promptStateKey(type)]
}

function isPromptCollapsed(type, text) {
	return isPromptCollapsible(text) && !isPromptExpanded(type)
}

function togglePrompt(type) {
	const key = promptStateKey(type)
	expandedPrompts.value = {
		...expandedPrompts.value,
		[key]: !expandedPrompts.value[key],
	}
}

function previewItem(item) {
	if (!detail.value || !item || !item.imageUrl) return
	const successful = detail.value.items.filter((candidate) => candidate.imageUrl)
	const urls = successful.map((candidate) => resolveUrl(candidate.imageUrl))
	const currentIndex = Math.max(0, successful.findIndex((candidate) => candidate.seq === item.seq))
	return previewGeneratedImages(urls, currentIndex, currentUserId.value)
}

function previewReference(reference) {
	if (!reference || !reference.url) return
	return previewGeneratedImages([resolveUrl(reference.url)], 0, currentUserId.value)
}

async function loadDetail(taskId) {
	loading.value = true
	errorText.value = ''
	try {
		detail.value = await getWorkBatchDetail(taskId)
		expandedPrompts.value = {}
		const firstItem = detail.value && detail.value.items && detail.value.items[0]
		selectedSeq.value = firstItem ? firstItem.seq : 1
	} catch (error) {
		detail.value = null
		errorText.value = (error && error.message) || '作品详情加载失败'
	} finally {
		loading.value = false
	}
}

function goBack() {
	uni.navigateBack()
}

onLoad((options) => {
	const taskId = options && options.taskId ? decodeURIComponent(options.taskId) : ''
	if (!taskId) {
		loading.value = false
		errorText.value = '缺少作品批次编号'
		return
	}
	loadDetail(taskId)
})
</script>

<style lang="scss" scoped>
.detail-page {
	min-height: 100vh;
	background: #F5F6FA;
}

.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background: #FFFFFF;
}

.nav-inner {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 16rpx;
}

.nav-back,
.nav-placeholder {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 52rpx;
	font-weight: 300;
	color: #1A1A1A;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #1A1A1A;
}

.content {
	height: 100vh;
}

.content-inner {
	box-sizing: border-box;
	padding-left: 24rpx;
	padding-right: 24rpx;
	padding-bottom: calc(48rpx + env(safe-area-inset-bottom));
}

.loading-state {
	display: flex;
	align-items: center;
	justify-content: center;
	padding-top: 220rpx;
	font-size: 27rpx;
	color: #9196A1;
}

.overview-card,
.section-card {
	margin-top: 24rpx;
	border-radius: 24rpx;
	background: #FFFFFF;
	box-shadow: 0 8rpx 28rpx rgba(20, 20, 40, 0.06);
}

.overview-card {
	padding: 26rpx;
}

.overview-top,
.overview-counts,
.section-heading,
.item-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.source-tag {
	display: inline-flex;
	align-items: center;
	height: 42rpx;
	padding: 0 16rpx;
	border-radius: 21rpx;
	font-size: 22rpx;
	color: #2262C6;
	background: #EAF2FF;
}

.source-tag.agent {
	color: #7044BC;
	background: #F1EBFF;
}

.overview-time {
	font-size: 22rpx;
	color: #9A9EA8;
}

.overview-title {
	display: block;
	margin-top: 22rpx;
	font-size: 34rpx;
	font-weight: 650;
	color: #17191F;
}

.overview-counts {
	justify-content: flex-start;
	gap: 24rpx;
	margin-top: 16rpx;
	font-size: 24rpx;
}

.success-count { color: #19805A; }
.failed-count { color: #C24E4E; }
.bean-count { color: #7A5A1C; }

.section-card {
	padding: 26rpx;
}

.section-title,
.item-title {
	font-size: 30rpx;
	font-weight: 600;
	color: #181A20;
}

.section-tip {
	font-size: 22rpx;
	color: #9A9EA8;
}

.album-grid,
.reference-grid {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 14rpx;
	margin-top: 22rpx;
}

.album-item {
	position: relative;
	box-sizing: border-box;
	aspect-ratio: 1;
	border: 4rpx solid transparent;
	border-radius: 18rpx;
	overflow: hidden;
	background: #EEF0F4;
}

.album-item.selected {
	border-color: #4F84EA;
}

.album-image,
.reference-image {
	display: block;
	width: 100%;
	height: 100%;
}

.failed-placeholder,
.missing-reference {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	width: 100%;
	height: 100%;
	gap: 8rpx;
	font-size: 21rpx;
	color: #A55A5A;
	background: #FFF2F2;
}

.failed-icon {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 34rpx;
	height: 34rpx;
	border: 2rpx solid #C96B6B;
	border-radius: 50%;
	font-size: 22rpx;
}

.image-seq,
.reference-seq {
	position: absolute;
	top: 8rpx;
	left: 8rpx;
	padding: 4rpx 10rpx;
	border-radius: 14rpx;
	font-size: 20rpx;
	color: #FFFFFF;
	background: rgba(0, 0, 0, 0.58);
}

.ai-image-label {
	position: absolute;
	right: 8rpx;
	bottom: 8rpx;
	padding: 4rpx 9rpx;
	border-radius: 8rpx;
	background: rgba(15, 23, 42, 0.72);
	font-size: 17rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1.2;
}

.ai-image-label--large {
	right: 16rpx;
	bottom: 16rpx;
	padding: 7rpx 14rpx;
	font-size: 22rpx;
}

.item-heading {
	padding-bottom: 22rpx;
	border-bottom: 1rpx solid #ECEEF2;
}

.item-status {
	font-size: 23rpx;
	color: #BE4B4B;
}

.item-status.succeeded { color: #19805A; }
.item-status.generating { color: #4F72B8; }

.selected-preview {
	position: relative;
	margin-top: 24rpx;
	border-radius: 20rpx;
	overflow: hidden;
	background: #EEF0F4;
}

.selected-image {
	display: block;
	width: 100%;
}

.preview-hint {
	position: absolute;
	left: 16rpx;
	bottom: 16rpx;
	padding: 8rpx 14rpx;
	border-radius: 18rpx;
	font-size: 21rpx;
	color: #FFFFFF;
	background: rgba(0, 0, 0, 0.55);
}

.prompt-block,
.reference-section {
	margin-top: 28rpx;
}

.block-label,
.parameter-label {
	display: block;
	font-size: 23rpx;
	color: #858B96;
}

.prompt-text {
	display: block;
	margin-top: 12rpx;
	font-size: 27rpx;
	line-height: 1.65;
	color: #282B32;
	white-space: pre-wrap;
	word-break: break-word;
}

.prompt-text--collapsed {
	max-height: 9.9em;
	overflow: hidden;
}

.prompt-toggle {
	display: block;
	width: fit-content;
	margin-top: 12rpx;
	margin-left: auto;
	font-size: 24rpx;
	color: #3F73D8;
}

.full-prompt {
	padding: 20rpx;
	border-radius: 16rpx;
	background: #F6F7F9;
}

.inline-heading {
	padding-top: 26rpx;
	border-top: 1rpx solid #ECEEF2;
}

.reference-item {
	min-width: 0;
}

.reference-image-wrap {
	position: relative;
	aspect-ratio: 1;
	border-radius: 16rpx;
	overflow: hidden;
	background: #EEF0F4;
}

.reference-source {
	display: block;
	margin-top: 8rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
	font-size: 20rpx;
	color: #858B96;
}

.parameter-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 16rpx;
	margin-top: 30rpx;
	padding-top: 26rpx;
	border-top: 1rpx solid #ECEEF2;
}

.parameter-item {
	padding: 18rpx;
	border-radius: 16rpx;
	background: #F6F7F9;
}

.parameter-value {
	display: block;
	margin-top: 8rpx;
	font-size: 27rpx;
	font-weight: 500;
	color: #292C33;
}

.am-performance-light .overview-card,
.am-performance-light .section-card {
	box-shadow: none;
}
</style>

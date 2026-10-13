<template>
	<view v-if="visible" class="work-detail" :style="{ paddingTop: statusBarHeight + 'px' }">
		<view class="work-detail-topbar">
			<view class="work-detail-back" @tap="emit('close')">
				<view class="work-detail-arrow" />
			</view>
			<text class="work-detail-title">作品详情</text>
		</view>

		<view v-if="loading || !detail" class="work-detail-loading">
			<text class="work-detail-loading-text">正在加载详情…</text>
		</view>

		<scroll-view v-else class="work-detail-scroll" scroll-y :show-scrollbar="false">
			<view v-if="detail.imageUrl" class="work-detail-hero" :style="heroStyle">
				<image class="work-detail-hero-image" :src="resolveUrl(detail.imageUrl)" mode="aspectFill" />
				<text class="ai-image-label">AI生成</text>
			</view>

			<view class="work-detail-body">
				<view class="detail-card">
					<view class="detail-card-head">
						<text class="detail-card-label">原提示词</text>
						<text class="detail-copy" @tap="copyPrompt">复制</text>
					</view>
					<text class="detail-prompt am-selectable-text" user-select>{{ detail.prompt }}</text>
				</view>

				<view class="detail-card">
					<text class="detail-card-label detail-card-label--block">生成参数</text>
					<view class="detail-row" v-for="row in parameterRows" :key="row.label">
						<text class="detail-row-label">{{ row.label }}</text>
						<text class="detail-row-value">{{ row.value }}</text>
					</view>
				</view>

				<view class="detail-card">
					<text class="detail-card-label detail-card-label--block">实际参考图</text>
					<view v-if="referenceImages.length" class="detail-refs">
						<view class="detail-ref" v-for="(url, index) in referenceImages" :key="url + index">
							<image class="detail-ref-image" :src="resolveUrl(url)" mode="aspectFill" @tap="previewReference(index)" />
							<text class="detail-ref-index">图{{ index + 1 }}</text>
						</view>
					</view>
					<text v-else class="detail-empty">文生图，无参考图</text>
				</view>
			</view>
		</scroll-view>

		<view v-if="detail" class="work-detail-actions">
			<view class="work-detail-button work-detail-button--secondary" @tap="emit('edit', detail)">
				<text class="work-detail-button-text work-detail-button-text--secondary">编辑</text>
			</view>
			<view class="work-detail-button" :class="{ 'work-detail-button--disabled': regenerating }" @tap="regenerate">
				<text class="work-detail-button-text">{{ regenerating ? '提交中…' : '按原参数重新生成' }}</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import config from '@/utils/config.js'

const props = defineProps({
	visible: { type: Boolean, default: false },
	loading: { type: Boolean, default: false },
	regenerating: { type: Boolean, default: false },
	statusBarHeight: { type: Number, default: 44 },
	detail: { type: Object, default: null },
})

const emit = defineEmits(['close', 'edit', 'regenerate'])

const referenceImages = computed(() =>
	Array.isArray(props.detail && props.detail.referenceImages)
		? props.detail.referenceImages.slice(0, 9)
		: []
)

const parameterRows = computed(() => {
	if (!props.detail) return []
	return [
		{ label: '模式', value: props.detail.mode === 'img2img' ? '图生图' : '文生图' },
		{ label: '分辨率', value: props.detail.resolution || '默认' },
		{ label: '比例', value: props.detail.aspectRatio || '默认' },
		{ label: '生成时间', value: props.detail.createTime || '未知' },
		{ label: '单张消耗', value: `${Number(props.detail.beanCost || 0)} 豆` },
	]
})

const heroStyle = computed(() => {
	const parts = String((props.detail && props.detail.aspectRatio) || '1:1').split(':')
	const width = Number(parts[0])
	const height = Number(parts[1])
	return `aspect-ratio: ${width > 0 && height > 0 ? `${width} / ${height}` : '1 / 1'};`
})

function resolveUrl(url) {
	if (!url) return ''
	if (/^https?:\/\//.test(url)) return url
	return config.baseUrl + url
}

function copyPrompt() {
	if (!props.detail || !props.detail.prompt) return
	uni.setClipboardData({ data: props.detail.prompt })
}

function previewReference(index) {
	uni.previewImage({
		current: index,
		urls: referenceImages.value.map(resolveUrl),
	})
}

function regenerate() {
	if (props.regenerating) return
	emit('regenerate', props.detail)
}
</script>

<style lang="scss" scoped>
.work-detail {
	position: fixed;
	inset: 0;
	z-index: 10000;
	box-sizing: border-box;
	background: #f6f7fb;
	display: flex;
	flex-direction: column;
}

.work-detail-topbar {
	height: 88rpx;
	padding: 0 28rpx;
	display: flex;
	align-items: center;
	gap: 24rpx;
	flex-shrink: 0;
}

.work-detail-back {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background: #ffffff;
	display: flex;
	align-items: center;
	justify-content: center;
}

.work-detail-arrow {
	width: 22rpx;
	height: 22rpx;
	border-left: 4rpx solid #4f84ea;
	border-bottom: 4rpx solid #4f84ea;
	transform: translateX(4rpx) rotate(45deg);
}

.work-detail-title {
	font-size: 34rpx;
	font-weight: 700;
	color: #0f172a;
}

.work-detail-scroll {
	flex: 1;
	min-height: 0;
}

.work-detail-loading {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
}

.work-detail-loading-text,
.detail-empty {
	font-size: 26rpx;
	color: #94a3b8;
}

.work-detail-hero {
	position: relative;
	margin: 0 20rpx;
	max-height: 760rpx;
	border-radius: 28rpx;
	overflow: hidden;
	background: #dfeafa;
}

.work-detail-hero-image {
	width: 100%;
	height: 100%;
	display: block;
}

.ai-image-label {
	position: absolute;
	right: 20rpx;
	bottom: 20rpx;
	padding: 7rpx 14rpx;
	border-radius: 11rpx;
	background: rgba(15, 23, 42, 0.72);
	font-size: 22rpx;
	font-weight: 700;
	color: #ffffff;
	line-height: 1.2;
}

.work-detail-body {
	padding: 4rpx 20rpx 40rpx;
}

.detail-card {
	margin-top: 24rpx;
	padding: 28rpx 30rpx;
	border: 3rpx solid transparent;
	border-radius: 28rpx;
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
}

.detail-card-head,
.detail-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.detail-card-head {
	margin-bottom: 16rpx;
}

.detail-card-label,
.detail-row-label {
	font-size: 24rpx;
	color: #94a3b8;
}

.detail-card-label--block {
	display: block;
	margin-bottom: 18rpx;
}

.detail-copy {
	font-size: 24rpx;
	color: #4f84ea;
}

.detail-prompt {
	display: block;
	font-size: 28rpx;
	line-height: 1.7;
	color: #475569;
	white-space: pre-wrap;
}

.detail-row {
	min-height: 72rpx;
	border-top: 2rpx solid #eef2f8;
}

.detail-row:first-of-type {
	border-top: none;
}

.detail-row-value {
	font-size: 26rpx;
	color: #1e293b;
}

.detail-refs {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 16rpx;
}

.detail-ref {
	position: relative;
	aspect-ratio: 1;
	border-radius: 20rpx;
	overflow: hidden;
	background: #e2ecf8;
}

.detail-ref-image {
	width: 100%;
	height: 100%;
}

.detail-ref-index {
	position: absolute;
	left: 8rpx;
	bottom: 8rpx;
	padding: 4rpx 10rpx;
	border-radius: 999rpx;
	background: rgba(15, 23, 42, 0.62);
	font-size: 20rpx;
	color: #fff;
}

.work-detail-actions {
	padding: 20rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
	display: grid;
	grid-template-columns: 0.72fr 1.28fr;
	gap: 16rpx;
	background: #f6f7fb;
	flex-shrink: 0;
}

.work-detail-button {
	height: 92rpx;
	border-radius: 28rpx;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	display: flex;
	align-items: center;
	justify-content: center;
}

.work-detail-button--secondary {
	background: #e7eef9;
}

.work-detail-button--disabled {
	opacity: 0.6;
}

.work-detail-button-text {
	font-size: 27rpx;
	font-weight: 700;
	color: #fff;
}

.work-detail-button-text--secondary {
	color: #4f84ea;
}
</style>

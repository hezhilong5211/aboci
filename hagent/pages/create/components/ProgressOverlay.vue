<template>
	<!-- 进度浮层(屏 12):fixed 全屏,出图轮询期间展示;由父组件 visible 控制显隐 -->
	<view v-if="visible" class="progress-overlay" :style="overlayVars">
		<!-- 顶栏:返回(收起浮层) + 居中标题 -->
		<view class="po-head">
			<view class="po-back" @tap="emit('close')">
				<text class="po-back-icon">‹</text>
			</view>
			<text class="po-title">生成中</text>
		</view>

		<view class="po-body">
			<!-- 环形进度:conic-gradient 外环 + 白色内圆中心数字 -->
			<view class="po-ring" :style="ringStyle">
				<view class="po-ring-inner">
					<text class="po-percent">{{ displayPercent }}<text class="po-percent-unit">%</text></text>
					<text class="po-ring-tip">生成中</text>
				</view>
			</view>

			<!-- 任务摘要:提示词/比例 × 张数 -->
			<text class="po-summary">{{ summaryText }}</text>
			<text class="po-eta">预计还需 ~{{ etaSeconds }} 秒 · 可离开页面，完成后通知</text>

			<!-- 步骤时间线:渐变描边卡 -->
			<view class="po-timeline">
				<view
					class="po-step"
					v-for="(step, idx) in steps"
					:key="step.key"
					:class="{ 'po-step--top': idx === 0, [`po-step--${stepState(idx)}`]: true }"
				>
					<view class="po-dot" :class="`po-dot--${stepState(idx)}`" />
					<text class="po-step-text" :class="{ 'po-step-text--active': stepState(idx) === 'active' }">{{ step.label }}</text>
					<!-- 已完成显示对勾;进行中/待办显示右侧副文案 -->
					<view v-if="stepState(idx) === 'done'" class="po-check">
						<text class="po-check-mark">✓</text>
					</view>
					<text v-else-if="step.tail" class="po-step-tail">{{ step.tail }}</text>
				</view>
			</view>

			<!-- 已预扣提示 -->
			<view class="po-notice">
				<view class="po-notice-icon">
					<text class="po-notice-icon-text">i</text>
				</view>
				<text class="po-notice-text">已预扣豆子，失败自动退还</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
	// 浮层显隐
	visible: {
		type: Boolean,
		default: false,
	},
	// 进度百分比(0-100,父组件据轮询 status/successCount 推进)
	percent: {
		type: Number,
		default: 0,
	},
	// 顶部安全区高度(px)
	statusBarHeight: {
		type: Number,
		default: 44,
	},
	// 任务摘要文案(如「高级感氛围短发 · 3:4 × 4」)
	summaryText: {
		type: String,
		default: '正在生成…',
	},
	// 当前推进到的步骤索引(0 任务已提交 / 1 排队中 / 2 正在生成 / 3 审核与下发)
	stepIndex: {
		type: Number,
		default: 0,
	},
})

const emit = defineEmits(['close'])

// 顶栏 padding-top 含状态栏高度
const overlayVars = computed(() => ({
	'--status-bar-height': `${props.statusBarHeight}px`,
}))

// 归一化百分比
const displayPercent = computed(() => Math.max(0, Math.min(100, Math.round(props.percent))))

// 环形 conic-gradient:主色填充至当前百分比,其余浅蓝
const ringStyle = computed(() => ({
	background: `conic-gradient(#4F84EA 0% ${displayPercent.value}%, #dbe7f6 ${displayPercent.value}% 100%)`,
}))

// 预计剩余秒数:按剩余进度粗估(纯展示,不精确)
const etaSeconds = computed(() => {
	const remain = 100 - displayPercent.value
	return Math.max(3, Math.round(remain * 0.3))
})

// 时间线步骤定义
const steps = [
	{ key: 'submitted', label: '任务已提交' },
	{ key: 'queued', label: '排队中 · 主队列', tail: '第 1 生产队列' },
	{ key: 'running', label: '正在生成…', tail: '' },
	{ key: 'review', label: '审核与下发' },
]

// 单步状态:已过为 done,当前为 active,未到为 pending
function stepState(idx) {
	if (idx < props.stepIndex) return 'done'
	if (idx === props.stepIndex) return 'active'
	return 'pending'
}
</script>

<style lang="scss" scoped>
.progress-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 900;
	background: #f6f7fb;
	display: flex;
	flex-direction: column;
}

/* 顶栏 */
.po-head {
	position: relative;
	height: 88rpx;
	padding-top: var(--status-bar-height);
	display: flex;
	align-items: center;
	justify-content: center;
}

.po-back {
	position: absolute;
	left: 24rpx;
	bottom: 0;
	width: 64rpx;
	height: 88rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.po-back-icon {
	font-size: 52rpx;
	color: #0f172a;
	line-height: 1;
}

.po-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #0f172a;
	line-height: 88rpx;
}

/* 主体 */
.po-body {
	flex: 1;
	padding: 60rpx 48rpx 0;
	display: flex;
	flex-direction: column;
	align-items: center;
}

/* 环形进度 */
.po-ring {
	width: 300rpx;
	height: 300rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
}

.po-ring-inner {
	width: 248rpx;
	height: 248rpx;
	border-radius: 50%;
	background: #ffffff;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	box-shadow: inset 0 0 0 2rpx #eef2f8;
}

.po-percent {
	font-size: 68rpx;
	font-weight: 800;
	color: #4f84ea;
	line-height: 1;
}

.po-percent-unit {
	font-size: 36rpx;
}

.po-ring-tip {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: #64748b;
	line-height: 1;
}

.po-summary {
	margin-top: 56rpx;
	font-size: 32rpx;
	font-weight: 600;
	color: #0f172a;
	text-align: center;
	line-height: 1.4;
}

.po-eta {
	margin-top: 16rpx;
	font-size: 26rpx;
	color: #64748b;
	text-align: center;
	line-height: 1.4;
}

/* 时间线渐变描边卡 */
.po-timeline {
	width: 100%;
	margin-top: 64rpx;
	border-radius: 32rpx;
	border: 3rpx solid transparent;
	background:
		linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	overflow: hidden;
}

.po-step {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 28rpx 32rpx;
	border-top: 2rpx solid #eef2f8;
}

.po-step--top {
	border-top: none;
}

.po-step--pending {
	opacity: 0.5;
}

.po-dot {
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	flex: none;
}

.po-dot--done {
	background: #16a34a;
}

.po-dot--active {
	background: #4f84ea;
	box-shadow: 0 0 16rpx #4f84ea;
}

.po-dot--pending {
	background: #cbd5e1;
}

.po-step-text {
	flex: 1;
	font-size: 28rpx;
	color: #1e293b;
	line-height: 1.3;
}

.po-step-text--active {
	color: #4f84ea;
}

.po-step-tail {
	font-size: 24rpx;
	color: #94a3b8;
	line-height: 1;
}

.po-check {
	width: 36rpx;
	height: 36rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.po-check-mark {
	font-size: 30rpx;
	color: #16a34a;
	line-height: 1;
}

/* 已预扣提示 */
.po-notice {
	display: flex;
	align-items: center;
	gap: 12rpx;
	margin-top: 40rpx;
	padding: 20rpx 32rpx;
	border-radius: 24rpx;
	background: #deeafb;
}

.po-notice-icon {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	background: #4f84ea;
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}

.po-notice-icon-text {
	font-size: 22rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 1;
}

.po-notice-text {
	font-size: 26rpx;
	color: #64748b;
	line-height: 1;
}
</style>

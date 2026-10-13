<template>
	<!-- 工具调用状态条:对应设计屏 04「正在生成图片 · 2 张」-->
	<view class="tool-card">
		<view class="tool-head">
			<!-- running 时转圈,done 时勾选 -->
			<view v-if="tool.status === 'running'" class="tool-spinner" />
			<text v-else class="tool-check">✓</text>
			<text class="tool-summary">{{ summaryText }}</text>
		</view>

		<!-- generate_image 运行中显示条纹占位图组(占位数取 n,默认 2) -->
		<view
			v-if="tool.name === 'generate_image' && tool.status === 'running'"
			class="tool-placeholders"
		>
			<view
				class="tool-placeholder"
				v-for="i in placeholderCount"
				:key="i"
			>
				<text class="tool-placeholder-text">生成中</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
	// 工具对象 { name, status:'running'|'done', summary, n? }
	tool: { type: Object, required: true },
})

// 摘要文案:优先后端 summary,缺省按工具名兜底
const summaryText = computed(() => {
	if (props.tool.summary) return props.tool.summary
	if (props.tool.name === 'recognize_image') {
		return props.tool.status === 'running' ? '正在识别图片…' : '已识别图片'
	}
	if (props.tool.name === 'generate_image') {
		return props.tool.status === 'running' ? '正在生成图片…' : '图片已生成'
	}
	return props.tool.status === 'running' ? '处理中…' : '已完成'
})

// 占位图数量:取工具 n,缺省 2,上限 4
const placeholderCount = computed(() => {
	const n = Number(props.tool.n)
	if (!n || n < 1) return 2
	return Math.min(n, 4)
})
</script>

<style lang="scss" scoped>
.tool-card {
	padding: 24rpx 28rpx;
	border-radius: 28rpx;
	background: #deeafb;
	border: 1rpx solid rgba(96, 140, 224, 0.18);
}

.tool-head {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

/* 转圈加载 */
.tool-spinner {
	width: 32rpx;
	height: 32rpx;
	border: 4rpx solid rgba(96, 140, 224, 0.35);
	border-top-color: #4f84ea;
	border-radius: 50%;
	flex-shrink: 0;
	animation: tool-spin 0.8s linear infinite;
}

@keyframes tool-spin {
	to {
		transform: rotate(360deg);
	}
}

.tool-check {
	width: 32rpx;
	height: 32rpx;
	line-height: 32rpx;
	text-align: center;
	font-size: 26rpx;
	font-weight: 700;
	color: #16a34a;
	flex-shrink: 0;
}

.tool-summary {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	font-weight: 600;
	color: #4f84ea;
	line-height: 1.4;
}

/* 条纹占位图组 */
.tool-placeholders {
	display: flex;
	gap: 20rpx;
	margin-top: 24rpx;
}

.tool-placeholder {
	flex: 1;
	aspect-ratio: 3 / 4;
	border-radius: 20rpx;
	/* 斜纹占位 */
	background: repeating-linear-gradient(
		135deg,
		#e2ecf8 0 14rpx,
		#eef4fc 14rpx 28rpx
	);
	display: flex;
	align-items: center;
	justify-content: center;
}

.tool-placeholder-text {
	font-size: 22rpx;
	color: #4f84ea;
}
</style>

<template>
	<!-- 思考折叠块:对应设计屏 04 / 04B,默认折叠,点标题切换 -->
	<view class="thinking-block">
		<!-- 标题条:科技描边卡,点击展开/折叠 -->
		<view class="thinking-head" @tap="toggle">
			<!-- 灯泡图标 -->
			<view class="thinking-icon">
				<text class="thinking-icon-glyph">💡</text>
			</view>
			<text class="thinking-title">{{ headText }}</text>
			<!-- 折叠箭头:展开时朝上,折叠时朝下 -->
			<text class="thinking-arrow" :class="{ 'thinking-arrow--open': expanded }">⌄</text>
		</view>

		<!-- 展开后的思考内容:左竖条 + 浅蓝底 -->
		<view class="thinking-body" v-if="expanded">
			<text class="thinking-text">{{ content }}</text>
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
	// 思考增量文本(由父组件随 onThinking 拼接)
	content: { type: String, default: '' },
	// 是否仍在流式输出(进行中显示「思考中」)
	loading: { type: Boolean, default: false },
})

// 默认折叠
const expanded = ref(false)

// 标题文案:流式中显示「思考中」,结束显示「已思考」
const headText = computed(() => (props.loading ? '思考中…' : '已思考'))

function toggle() {
	expanded.value = !expanded.value
}
</script>

<style lang="scss" scoped>
.thinking-block {
	width: 100%;
}

/* 标题条:科技描边卡 */
.thinking-head {
	display: flex;
	align-items: center;
	gap: 16rpx;
	padding: 20rpx 28rpx;
	border-radius: 24rpx;
	/* 内白外科技渐变描边 */
	background: linear-gradient(#fff, #fff) padding-box,
		linear-gradient(135deg, #7fb0f7, #a0c4fb) border-box;
	border: 3rpx solid transparent;
}

.thinking-head:active {
	opacity: 0.92;
}

.thinking-icon {
	width: 30rpx;
	height: 30rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.thinking-icon-glyph {
	font-size: 26rpx;
	line-height: 1;
}

.thinking-title {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	color: #64748b;
	line-height: 1.4;
}

.thinking-arrow {
	flex-shrink: 0;
	font-size: 32rpx;
	color: #94a3b8;
	line-height: 1;
	transition: transform 0.2s ease;
}

.thinking-arrow--open {
	transform: rotate(180deg);
	color: #4f84ea;
}

/* 思考内容:左竖条 + 浅蓝底 */
.thinking-body {
	margin-top: 16rpx;
	padding: 24rpx 28rpx;
	border-left: 4rpx solid #c7d6f2;
	background: #f4f7fc;
	border-radius: 0 20rpx 20rpx 0;
}

.thinking-text {
	font-size: 26rpx;
	line-height: 1.85;
	color: #64748b;
	white-space: pre-wrap;
	word-break: break-word;
}
</style>

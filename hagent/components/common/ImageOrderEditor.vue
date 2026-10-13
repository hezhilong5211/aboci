<template>
	<view class="order-mask" v-if="visible" @tap="cancel">
		<view class="order-panel" @tap.stop>
			<view class="order-head">
				<text class="order-title">调整图片顺序</text>
				<text class="order-subtitle">点击左右箭头调整，完成后保存</text>
			</view>

			<view class="order-grid">
				<view
					class="order-item"
					v-for="(item, index) in draft"
					:key="index"
				>
					<view class="order-image-wrap">
						<image class="order-image" :src="item.url" mode="aspectFill" />
						<text class="order-index">图{{ index + 1 }}</text>
					</view>
					<view class="order-controls">
						<view
							class="order-arrow"
							:class="{ 'order-arrow--disabled': index === 0 }"
							@tap="move(index, -1)"
						>←</view>
						<view
							class="order-arrow"
							:class="{ 'order-arrow--disabled': index === draft.length - 1 }"
							@tap="move(index, 1)"
						>→</view>
					</view>
				</view>
			</view>

			<view class="order-actions">
				<view class="order-button order-button--cancel" @tap="cancel">取消</view>
				<view class="order-button order-button--confirm" @tap="confirm">完成</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
	visible: { type: Boolean, default: false },
	urls: { type: Array, default: () => [] },
})

const emit = defineEmits(['cancel', 'confirm'])

const draft = ref([])

watch(
	() => [props.visible, props.urls],
	() => {
		if (!props.visible) return
		draft.value = props.urls.map((url, index) => ({ sourceIndex: index, url }))
	},
	{ immediate: true, deep: true }
)

function move(index, offset) {
	const target = index + offset
	if (target < 0 || target >= draft.value.length) return
	const next = draft.value.slice()
	const current = next[index]
	next[index] = next[target]
	next[target] = current
	draft.value = next
}

function cancel() {
	emit('cancel')
}

function confirm() {
	emit('confirm', draft.value.map((item) => item.sourceIndex))
}
</script>

<style lang="scss" scoped>
.order-mask {
	position: fixed;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	z-index: 120;
	background: rgba(15, 23, 42, 0.45);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 40rpx;
	box-sizing: border-box;
}

.order-panel {
	width: 640rpx;
	padding: 36rpx;
	box-sizing: border-box;
	border-radius: 32rpx;
	background: #ffffff;
	box-shadow: 0 24rpx 80rpx rgba(15, 23, 42, 0.18);
}

.order-head {
	display: flex;
	flex-direction: column;
	gap: 10rpx;
	margin-bottom: 28rpx;
}

.order-title {
	font-size: 32rpx;
	font-weight: 700;
	color: #1e293b;
}

.order-subtitle {
	font-size: 24rpx;
	color: #94a3b8;
}

.order-grid {
	width: 568rpx;
	display: grid;
	grid-template-columns: repeat(3, 176rpx);
	gap: 24rpx 20rpx;
}

.order-item {
	width: 176rpx;
}

.order-image-wrap {
	position: relative;
	width: 176rpx;
	height: 176rpx;
	border-radius: 22rpx;
	overflow: hidden;
	box-shadow: 0 8rpx 22rpx rgba(79, 132, 234, 0.12);
}

.order-image {
	width: 100%;
	height: 100%;
}

.order-index {
	position: absolute;
	left: 10rpx;
	bottom: 10rpx;
	padding: 3rpx 10rpx;
	border-radius: 10rpx;
	background: rgba(15, 23, 42, 0.66);
	font-size: 22rpx;
	color: #ffffff;
	line-height: 1.4;
}

.order-controls {
	display: flex;
	gap: 12rpx;
	margin-top: 12rpx;
}

.order-arrow {
	flex: 1;
	height: 52rpx;
	border-radius: 26rpx;
	background: #edf4ff;
	color: #4f84ea;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 30rpx;
	font-weight: 700;
}

.order-arrow--disabled {
	background: #f1f5f9;
	color: #cbd5e1;
}

.order-actions {
	display: flex;
	gap: 20rpx;
	margin-top: 32rpx;
}

.order-button {
	flex: 1;
	height: 80rpx;
	border-radius: 40rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	font-weight: 600;
}

.order-button--cancel {
	background: #f1f5f9;
	color: #64748b;
}

.order-button--confirm {
	background: linear-gradient(135deg, #7aa6f8, #5f8bea);
	color: #ffffff;
}
</style>

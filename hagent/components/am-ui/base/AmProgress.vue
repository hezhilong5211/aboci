<template>
	<view class="am-progress">
		<view class="am-progress__bar" :style="{ height }">
			<view class="am-progress__fill" :style="{ width: normalizedPercent + '%' }" />
		</view>
		<text class="am-progress__text" v-if="showText">{{ normalizedPercent }}%</text>
	</view>
</template>

<script>
export default {
	options: {
		styleIsolation: 'shared',
		virtualHost: true
	}
}
</script>

<script setup>


import { computed } from 'vue'

const props = defineProps({
	percent: {
		type: Number,
		default: 0,
	},
	height: {
		type: String,
		default: '12rpx',
	},
	showText: {
		type: Boolean,
		default: false,
	},
})

const normalizedPercent = computed(() => Math.max(0, Math.min(100, Math.round(props.percent))))
</script>

<style lang="scss" scoped>
.am-progress {
	display: flex;
	align-items: center;
	gap: 16rpx;
	width: 100%;
}

.am-progress__bar {
	flex: 1;
	border-radius: 999rpx;
	background: rgba(187, 211, 255, 0.86);
	overflow: hidden;
}

.am-progress__fill {
	height: 100%;
	border-radius: 999rpx;
	background: linear-gradient(90deg, #2f7cff 0%, #6eaaff 100%);
}

.am-progress__text {
	font-size: 22rpx;
	color: #65758f;
	line-height: 1;
}
</style>

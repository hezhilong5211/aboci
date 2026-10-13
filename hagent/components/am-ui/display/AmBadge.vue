<template>
	<view class="am-badge">
		<slot />
		<view class="am-badge__value" :class="{ 'am-badge__value--dot': dot }" v-if="show">
			<text class="am-badge__text" v-if="!dot">{{ displayValue }}</text>
		</view>
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
	value: {
		type: [Number, String],
		default: '',
	},
	max: {
		type: Number,
		default: 99,
	},
	dot: {
		type: Boolean,
		default: false,
	},
	show: {
		type: Boolean,
		default: true,
	},
})

const displayValue = computed(() => {
	if (typeof props.value === 'number' && props.value > props.max) return `${props.max}+`
	return String(props.value)
})
</script>

<style lang="scss" scoped>
.am-badge {
	position: relative;
	display: inline-flex;
}

.am-badge__value {
	position: absolute;
	top: -10rpx;
	right: -16rpx;
	min-width: 32rpx;
	height: 32rpx;
	padding: 0 8rpx;
	border-radius: 999rpx;
	background: #ff4d5b;
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.am-badge__value--dot {
	top: -4rpx;
	right: -4rpx;
	width: 16rpx;
	min-width: 16rpx;
	height: 16rpx;
	padding: 0;
}

.am-badge__text {
	font-size: 18rpx;
	color: #ffffff;
	font-weight: 700;
	line-height: 1;
}
</style>

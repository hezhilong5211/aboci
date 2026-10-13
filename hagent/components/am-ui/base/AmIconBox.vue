<template>
	<view class="am-icon-box" :style="boxStyle">
		<slot>
			<text class="am-icon-box__text" :style="{ color }">{{ icon }}</text>
		</slot>
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
	icon: {
		type: String,
		default: '',
	},
	background: {
		type: String,
		default: 'linear-gradient(135deg, #5b9dff 0%, #3e7dff 100%)',
	},
	size: {
		type: String,
		default: '84rpx',
	},
	radius: {
		type: String,
		default: '28rpx',
	},
	color: {
		type: String,
		default: '#ffffff',
	},
	shadow: {
		type: Boolean,
		default: true,
	},
})

const boxStyle = computed(() => ({
	width: props.size,
	height: props.size,
	borderRadius: props.radius,
	background: props.background,
	boxShadow: props.shadow ? '0 12rpx 24rpx rgba(84, 136, 221, 0.12)' : 'none',
}))
</script>

<style lang="scss" scoped>
.am-icon-box {
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.am-icon-box__text {
	font-size: 38rpx;
	font-weight: 700;
	line-height: 1;
}

.am-performance-light .am-icon-box {
	box-shadow: none !important;
}
</style>

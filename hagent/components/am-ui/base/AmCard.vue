<template>
	<view
		class="am-card"
		:class="[`am-card--${variant}`, { 'am-card--clickable': clickable }]"
		:style="cardStyle"
		:hover-class="clickable ? 'am-tap-feedback' : 'none'"
		:hover-stay-time="70"
		@tap="handleTap"
	>
		<slot />
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
	variant: {
		type: String,
		default: 'default',
	},
	clickable: {
		type: Boolean,
		default: false,
	},
	padding: {
		type: String,
		default: '',
	},
	radius: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['tap'])

const cardStyle = computed(() => {
	const style = {}
	if (props.padding) style.padding = props.padding
	if (props.radius) style.borderRadius = props.radius
	return style
})

function handleTap(event) {
	if (!props.clickable) return
	emit('tap', event)
}
</script>

<style lang="scss" scoped>
.am-card {
	box-sizing: border-box;
	border-radius: 28rpx;
	background: #ffffff;
	box-shadow: 0 8rpx 24rpx rgba(80, 115, 170, 0.05);
}

.am-card--soft {
	background: linear-gradient(180deg, #eef5ff 0%, #e8f1ff 100%);
	border: 2rpx solid rgba(255, 255, 255, 0.72);
	box-shadow: 0 12rpx 30rpx rgba(110, 157, 234, 0.08);
}

.am-card--blue {
	background: linear-gradient(180deg, rgba(191, 219, 255, 0.88) 0%, rgba(210, 230, 255, 0.92) 100%);
}

.am-card--clickable {
	transition: opacity 0.15s ease;
}

.am-card--clickable:active {
	opacity: 0.86;
}

.am-performance-light .am-card {
	box-shadow: none;
}

.am-performance-light .am-card--soft,
.am-performance-light .am-card--blue {
	background: #eef5ff;
}

.am-performance-light .am-card--clickable {
	transition: none;
}
</style>

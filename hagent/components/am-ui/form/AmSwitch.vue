<template>
	<view
		class="am-switch"
		:class="{ 'am-switch--checked': modelValue, 'am-switch--disabled': disabled }"
		@tap="handleTap"
	>
		<view class="am-switch__thumb" />
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


const props = defineProps({
	modelValue: {
		type: Boolean,
		default: false,
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'change'])

function handleTap() {
	if (props.disabled) return
	const next = !props.modelValue
	emit('update:modelValue', next)
	emit('change', next)
}
</script>

<style lang="scss" scoped>
.am-switch {
	width: 72rpx;
	height: 40rpx;
	border-radius: 999rpx;
	background: #c8d0dc;
	padding: 4rpx;
	box-sizing: border-box;
	transition: background 0.16s ease;
}

.am-switch--checked {
	background: #2f6bff;
}

.am-switch--disabled {
	opacity: 0.45;
}

.am-switch__thumb {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	background: #ffffff;
	transform: translateX(0);
	transition: transform 0.16s ease;
	box-shadow: 0 2rpx 8rpx rgba(17, 24, 39, 0.12);
}

.am-switch--checked .am-switch__thumb {
	transform: translateX(32rpx);
}

.am-performance-light .am-switch,
.am-performance-light .am-switch__thumb {
	transition: none;
}

.am-performance-light .am-switch__thumb {
	box-shadow: none;
}
</style>

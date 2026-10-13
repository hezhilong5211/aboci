<template>
	<view
		class="am-button"
		:class="[
			`am-button--${variant}`,
			`am-button--${size}`,
			{
				'am-button--block': block,
				'am-button--disabled': disabled || loading,
			},
		]"
		:hover-class="disabled || loading ? 'none' : 'am-tap-feedback'"
		:hover-stay-time="70"
		@tap="handleTap"
	>
		<text class="am-button__loading" v-if="loading">○</text>
		<text class="am-button__text"><slot>{{ text }}</slot></text>
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
	text: {
		type: String,
		default: '',
	},
	variant: {
		type: String,
		default: 'primary',
	},
	size: {
		type: String,
		default: 'medium',
	},
	block: {
		type: Boolean,
		default: false,
	},
	disabled: {
		type: Boolean,
		default: false,
	},
	loading: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])

function handleTap(event) {
	if (props.disabled || props.loading) return
	emit('tap', event)
}
</script>

<style lang="scss" scoped>
.am-button {
	box-sizing: border-box;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	border-radius: 16rpx;
	border: 2rpx solid transparent;
	transition: opacity 0.15s ease;
}

.am-button:active {
	opacity: 0.82;
}

.am-button--block {
	width: 100%;
}

.am-button--large {
	height: 88rpx;
	padding: 0 36rpx;
}

.am-button--medium {
	height: 72rpx;
	padding: 0 30rpx;
}

.am-button--small {
	height: 56rpx;
	padding: 0 22rpx;
	border-radius: 12rpx;
}

.am-button--primary {
	background: #2f6bff;
	color: #ffffff;
}

.am-button--secondary {
	background: #ffffff;
	border-color: #2f6bff;
	color: #2f6bff;
}

.am-button--danger {
	background: #ff5a5f;
	color: #ffffff;
}

.am-button--text {
	background: transparent;
	color: #2f6bff;
}

.am-button--disabled {
	opacity: 0.48;
}

.am-button__text,
.am-button__loading {
	font-size: 26rpx;
	font-weight: 600;
	line-height: 1;
	color: currentColor;
}

.am-performance-light .am-button {
	transition: none;
}
</style>

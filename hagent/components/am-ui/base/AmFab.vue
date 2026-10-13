<template>
	<view
		class="am-fab"
		:class="[
			`am-fab--${type}`,
			{
				'am-fab--fixed': fixed,
				'am-fab--disabled': disabled,
				'am-fab--extended': text,
			},
		]"
		:style="fabStyle"
		@tap="handleTap"
	>
		<text class="am-fab__icon">{{ icon }}</text>
		<text class="am-fab__text" v-if="text">{{ text }}</text>
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
		default: '＋',
	},
	text: {
		type: String,
		default: '',
	},
	type: {
		type: String,
		default: 'primary',
	},
	fixed: {
		type: Boolean,
		default: true,
	},
	right: {
		type: String,
		default: '32rpx',
	},
	bottom: {
		type: String,
		default: '160rpx',
	},
	disabled: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])
const fabStyle = computed(() => props.fixed ? {
	right: props.right,
	bottom: `calc(${props.bottom} + env(safe-area-inset-bottom))`,
} : {})

function handleTap(event) {
	if (props.disabled) return
	emit('tap', event)
}
</script>

<style lang="scss" scoped>
.am-fab {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	background: var(--am-color-primary);
	box-shadow: 0 12rpx 30rpx rgba(47, 107, 255, 0.22);
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	box-sizing: border-box;
}

.am-fab--fixed {
	position: fixed;
	z-index: 40;
}

.am-fab--extended {
	width: auto;
	min-width: 152rpx;
	padding: 0 28rpx;
	border-radius: 999rpx;
}

.am-fab--success {
	background: var(--am-color-success);
	box-shadow: 0 12rpx 30rpx rgba(34, 197, 94, 0.18);
}

.am-fab--warning {
	background: var(--am-color-warning);
	box-shadow: 0 12rpx 30rpx rgba(255, 176, 32, 0.18);
}

.am-fab--disabled {
	opacity: 0.48;
}

.am-fab__icon,
.am-fab__text {
	color: #ffffff;
	line-height: 1;
}

.am-fab__icon {
	font-size: 36rpx;
	font-weight: 700;
}

.am-fab__text {
	font-size: 26rpx;
	font-weight: 700;
}

.am-performance-light .am-fab {
	box-shadow: none;
}
</style>

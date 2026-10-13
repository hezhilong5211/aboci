<template>
	<view class="am-toast" :class="`am-toast--${type}`" v-if="visible">
		<text class="am-toast__icon">{{ iconText }}</text>
		<text class="am-toast__text">{{ text }}</text>
		<text class="am-toast__close" v-if="closeable" @tap="emit('close')">×</text>
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
	visible: {
		type: Boolean,
		default: false,
	},
	type: {
		type: String,
		default: 'success',
	},
	text: {
		type: String,
		default: '',
	},
	closeable: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['close'])

const iconText = computed(() => {
	const map = {
		success: '✓',
		info: 'i',
		warning: '!',
		error: '×',
	}
	return map[props.type] || map.info
})
</script>

<style lang="scss" scoped>
.am-toast {
	min-height: 72rpx;
	border-radius: 12rpx;
	padding: 0 22rpx;
	display: flex;
	align-items: center;
	gap: 16rpx;
	box-sizing: border-box;
}

.am-toast--success {
	background: #eefaf2;
	border: 1rpx solid #ccebd5;
}

.am-toast--info {
	background: #f4f6f9;
	border: 1rpx solid #e1e7ef;
}

.am-toast--warning {
	background: #fff7e8;
	border: 1rpx solid #ffe3ad;
}

.am-toast--error {
	background: #fff0f1;
	border: 1rpx solid #ffc9cc;
}

.am-toast__icon {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	background: #2f6bff;
	color: #ffffff;
	font-size: 22rpx;
	font-weight: 700;
	line-height: 32rpx;
	text-align: center;
	flex-shrink: 0;
}

.am-toast--success .am-toast__icon {
	background: #22c55e;
}

.am-toast--warning .am-toast__icon {
	background: #ffb020;
}

.am-toast--error .am-toast__icon {
	background: #ff5a5f;
}

.am-toast__text {
	flex: 1;
	font-size: 26rpx;
	color: #334155;
	line-height: 36rpx;
}

.am-toast__close {
	font-size: 30rpx;
	color: #64748b;
	line-height: 1;
	padding-left: 8rpx;
}
</style>

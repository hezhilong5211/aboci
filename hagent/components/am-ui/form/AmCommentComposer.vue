<template>
	<view class="am-comment-composer" :class="{ 'am-comment-composer--fixed': fixed }">
		<view class="am-comment-composer__reply" v-if="replyTo">
			<text class="am-comment-composer__reply-text">回复 {{ replyTo }}</text>
			<text class="am-comment-composer__reply-close" @tap="emit('clearReply')">×</text>
		</view>
		<view class="am-comment-composer__row">
			<input class="am-comment-composer__input" :value="modelValue" :placeholder="placeholder" @input="handleInput" />
			<view class="am-comment-composer__icon" @tap="emit('emoji')">
				<text class="am-comment-composer__icon-text">☺</text>
			</view>
			<AmButton size="small" :text="sendText" :disabled="!modelValue" @tap="emit('send')" />
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


import AmButton from '../base/AmButton.vue'

defineProps({
	modelValue: {
		type: String,
		default: '',
	},
	replyTo: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '说点什么...',
	},
	sendText: {
		type: String,
		default: '发送',
	},
	fixed: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue', 'send', 'emoji', 'clearReply'])

function handleInput(event) {
	emit('update:modelValue', event.detail.value)
}
</script>

<style lang="scss" scoped>
.am-comment-composer {
	padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
	background: #ffffff;
	border-top: 1rpx solid var(--am-color-border-light);
	box-sizing: border-box;
}

.am-comment-composer--fixed {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 30;
}

.am-comment-composer__reply {
	height: 52rpx;
	padding: 0 18rpx;
	margin-bottom: 12rpx;
	border-radius: 14rpx;
	background: var(--am-color-bg-muted);
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.am-comment-composer__reply-text,
.am-comment-composer__reply-close {
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}

.am-comment-composer__row {
	display: flex;
	align-items: center;
	gap: 14rpx;
}

.am-comment-composer__input {
	flex: 1;
	height: 68rpx;
	padding: 0 22rpx;
	border-radius: 999rpx;
	background: var(--am-color-bg-muted);
	font-size: 24rpx;
	color: var(--am-color-text-title);
	box-sizing: border-box;
}

.am-comment-composer__icon {
	width: 48rpx;
	height: 48rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-comment-composer__icon-text {
	font-size: 30rpx;
	color: var(--am-color-text-secondary);
	line-height: 1;
}
</style>

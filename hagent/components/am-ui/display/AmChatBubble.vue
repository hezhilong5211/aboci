<template>
	<view class="am-chat-bubble" :class="{ 'am-chat-bubble--self': self }">
		<view class="am-chat-bubble__avatar" v-if="showAvatar" @tap="emit('avatarTap')">
			<AmAvatar :src="avatar" :text="avatarText" size="72rpx" />
		</view>
		<view class="am-chat-bubble__main">
			<view class="am-chat-bubble__meta" v-if="name || time">
				<text class="am-chat-bubble__name" v-if="name">{{ name }}</text>
				<text class="am-chat-bubble__time" v-if="time">{{ time }}</text>
			</view>
			<view class="am-chat-bubble__content">
				<text class="am-chat-bubble__text">{{ text }}</text>
			</view>
			<text class="am-chat-bubble__status" v-if="status">{{ status }}</text>
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
import AmAvatar from './AmAvatar.vue'

const props = defineProps({
	type: {
		type: String,
		default: 'other',
	},
	avatar: {
		type: String,
		default: '',
	},
	avatarText: {
		type: String,
		default: '',
	},
	name: {
		type: String,
		default: '',
	},
	text: {
		type: String,
		default: '',
	},
	time: {
		type: String,
		default: '',
	},
	status: {
		type: String,
		default: '',
	},
	showAvatar: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['avatarTap'])
const self = computed(() => props.type === 'self')
</script>

<style lang="scss" scoped>
.am-chat-bubble {
	display: flex;
	align-items: flex-start;
	gap: 16rpx;
	margin-bottom: 26rpx;
}

.am-chat-bubble--self {
	flex-direction: row-reverse;
}

.am-chat-bubble__avatar {
	flex-shrink: 0;
}

.am-chat-bubble__main {
	max-width: 520rpx;
	display: flex;
	flex-direction: column;
	align-items: flex-start;
}

.am-chat-bubble--self .am-chat-bubble__main {
	align-items: flex-end;
}

.am-chat-bubble__meta {
	display: flex;
	align-items: center;
	gap: 12rpx;
	margin-bottom: 8rpx;
}

.am-chat-bubble--self .am-chat-bubble__meta {
	flex-direction: row-reverse;
}

.am-chat-bubble__name {
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}

.am-chat-bubble__time,
.am-chat-bubble__status {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-chat-bubble__content {
	padding: 20rpx 24rpx;
	border-radius: 8rpx 24rpx 24rpx 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-chat-bubble--self .am-chat-bubble__content {
	border-radius: 24rpx 8rpx 24rpx 24rpx;
	background: var(--am-color-primary);
}

.am-chat-bubble__text {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	line-height: 40rpx;
}

.am-chat-bubble--self .am-chat-bubble__text {
	color: #ffffff;
}

.am-chat-bubble__status {
	margin-top: 6rpx;
}
</style>

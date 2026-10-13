<template>
	<view class="am-comment-item">
		<AmAvatar :src="avatar" :text="author" size="72rpx" />
		<view class="am-comment-item__main">
			<view class="am-comment-item__header">
				<text class="am-comment-item__author">{{ author }}</text>
				<text class="am-comment-item__time" v-if="time">{{ time }}</text>
			</view>
			<text class="am-comment-item__content">{{ content }}</text>
			<view class="am-comment-item__footer" v-if="replyText || likeText">
				<text class="am-comment-item__action" v-if="replyText" @tap="emit('reply')">{{ replyText }}</text>
				<text class="am-comment-item__action" v-if="likeText" @tap="emit('like')">{{ likeText }}</text>
			</view>
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


import AmAvatar from './AmAvatar.vue'

defineProps({
	avatar: {
		type: String,
		default: '',
	},
	author: {
		type: String,
		default: '',
	},
	content: {
		type: String,
		default: '',
	},
	time: {
		type: String,
		default: '',
	},
	replyText: {
		type: String,
		default: '回复',
	},
	likeText: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['reply', 'like'])
</script>

<style lang="scss" scoped>
.am-comment-item {
	display: flex;
	align-items: flex-start;
	gap: 18rpx;
}

.am-comment-item__main {
	flex: 1;
	min-width: 0;
}

.am-comment-item__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-comment-item__author {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
}

.am-comment-item__time {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-comment-item__content {
	display: block;
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--am-color-text-body);
	line-height: 38rpx;
}

.am-comment-item__footer {
	display: flex;
	align-items: center;
	gap: 22rpx;
	margin-top: 12rpx;
}

.am-comment-item__action {
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 30rpx;
}
</style>

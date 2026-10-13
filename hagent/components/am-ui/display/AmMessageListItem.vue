<template>
	<view class="am-message-list-item" @tap="emit('tap', message)">
		<AmAvatar :src="message.avatar" :text="message.nameText || message.title" size="72rpx" />
		<view class="am-message-list-item__main">
			<view class="am-message-list-item__title-row">
				<text class="am-message-list-item__title">{{ message.title }}</text>
				<text class="am-message-list-item__time">{{ message.time }}</text>
			</view>
			<view class="am-message-list-item__desc-row">
				<text class="am-message-list-item__desc">{{ message.desc }}</text>
				<image class="am-message-list-item__cover" v-if="message.cover" :src="message.cover" mode="aspectFill" />
				<text class="am-message-list-item__mute" v-if="message.muted">⌁</text>
				<text class="am-message-list-item__dot" v-else-if="message.unread" />
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
	message: {
		type: Object,
		default: () => ({}),
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-message-list-item {
	display: flex;
	align-items: center;
	gap: 18rpx;
	padding: 18rpx 0;
}

.am-message-list-item__main {
	flex: 1;
	min-width: 0;
}

.am-message-list-item__title-row,
.am-message-list-item__desc-row {
	display: flex;
	align-items: center;
	gap: 14rpx;
	min-width: 0;
}

.am-message-list-item__title {
	flex: 1;
	min-width: 0;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.am-message-list-item__time {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
	flex-shrink: 0;
}

.am-message-list-item__desc-row {
	margin-top: 4rpx;
}

.am-message-list-item__desc {
	flex: 1;
	min-width: 0;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.am-message-list-item__cover {
	width: 54rpx;
	height: 54rpx;
	border-radius: 10rpx;
	background: var(--am-color-bg-muted);
	flex-shrink: 0;
}

.am-message-list-item__dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: var(--am-color-primary);
	flex-shrink: 0;
}

.am-message-list-item__mute {
	font-size: 24rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
	flex-shrink: 0;
}
</style>

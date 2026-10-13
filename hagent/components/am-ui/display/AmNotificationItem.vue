<template>
	<view class="am-notification-item" @tap="emit('tap')">
		<AmAvatar :src="avatar" :text="title" size="72rpx" />
		<view class="am-notification-item__main">
			<view class="am-notification-item__header">
				<text class="am-notification-item__title">{{ title }}</text>
				<text class="am-notification-item__time">{{ time }}</text>
			</view>
			<text class="am-notification-item__desc">{{ desc }}</text>
		</view>
		<AmButton v-if="actionText" size="small" :text="actionText" @tap.stop="emit('action')" />
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
import AmAvatar from './AmAvatar.vue'

defineProps({
	avatar: {
		type: String,
		default: '',
	},
	title: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	time: {
		type: String,
		default: '',
	},
	actionText: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['tap', 'action'])
</script>

<style lang="scss" scoped>
.am-notification-item {
	display: flex;
	align-items: center;
	gap: 18rpx;
	padding: 20rpx 0;
	border-bottom: 1rpx solid var(--am-color-border-light);
}

.am-notification-item:last-child {
	border-bottom: none;
}

.am-notification-item__main {
	flex: 1;
	min-width: 0;
}

.am-notification-item__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 18rpx;
}

.am-notification-item__title {
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
}

.am-notification-item__time {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}

.am-notification-item__desc {
	display: block;
	margin-top: 4rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}
</style>

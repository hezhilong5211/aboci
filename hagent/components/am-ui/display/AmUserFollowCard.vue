<template>
	<view class="am-user-follow-card" @tap="emit('tap')">
		<AmAvatar :src="avatar" :text="name" size="112rpx" />
		<text class="am-user-follow-card__name">{{ name }}</text>
		<text class="am-user-follow-card__desc">{{ desc }}</text>
		<view class="am-user-follow-card__stats">
			<text class="am-user-follow-card__stat">{{ posts }} 关注</text>
			<text class="am-user-follow-card__stat">{{ fans }} 粉丝</text>
		</view>
		<AmButton block :text="followed ? '已关注' : '关注'" :variant="followed ? 'secondary' : 'primary'" @tap.stop="emit('follow')" />
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
	name: {
		type: String,
		default: '',
	},
	desc: {
		type: String,
		default: '',
	},
	posts: {
		type: String,
		default: '0',
	},
	fans: {
		type: String,
		default: '0',
	},
	followed: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap', 'follow'])
</script>

<style lang="scss" scoped>
.am-user-follow-card {
	padding: 34rpx 28rpx;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	display: flex;
	flex-direction: column;
	align-items: center;
	text-align: center;
	box-sizing: border-box;
}

.am-user-follow-card__name {
	margin-top: 18rpx;
	font-size: 30rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 40rpx;
}

.am-user-follow-card__desc {
	margin-top: 8rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.am-user-follow-card__stats {
	display: flex;
	align-items: center;
	gap: 26rpx;
	margin: 22rpx 0;
}

.am-user-follow-card__stat {
	font-size: 22rpx;
	color: var(--am-color-text-body);
	line-height: 30rpx;
}
</style>

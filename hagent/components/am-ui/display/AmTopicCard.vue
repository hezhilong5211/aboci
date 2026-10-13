<template>
	<view class="am-topic-card" @tap="emit('tap')">
		<image class="am-topic-card__cover" v-if="cover" :src="cover" mode="aspectFill" />
		<view class="am-topic-card__main">
			<view class="am-topic-card__title-row">
				<text class="am-topic-card__title"># {{ title }}</text>
				<AmTag v-if="hot" text="热" type="danger" />
			</view>
			<text class="am-topic-card__desc" v-if="desc">{{ desc }}</text>
			<view class="am-topic-card__meta">
				<text class="am-topic-card__meta-text">{{ posts }} 帖子</text>
				<text class="am-topic-card__meta-text">{{ follows }} 关注</text>
			</view>
		</view>
		<AmButton size="small" :text="followed ? '已关注' : '关注'" :variant="followed ? 'secondary' : 'primary'" @tap.stop="emit('follow')" />
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
import AmTag from '../base/AmTag.vue'

defineProps({
	cover: {
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
	posts: {
		type: String,
		default: '0',
	},
	follows: {
		type: String,
		default: '0',
	},
	hot: {
		type: Boolean,
		default: false,
	},
	followed: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap', 'follow'])
</script>

<style lang="scss" scoped>
.am-topic-card {
	display: flex;
	align-items: center;
	gap: 18rpx;
	padding: 24rpx;
	border-radius: 22rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.am-topic-card__cover {
	width: 92rpx;
	height: 92rpx;
	border-radius: 16rpx;
	background: var(--am-color-bg-muted);
	flex-shrink: 0;
}

.am-topic-card__main {
	flex: 1;
	min-width: 0;
}

.am-topic-card__title-row {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.am-topic-card__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-topic-card__desc {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 32rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-topic-card__meta {
	display: flex;
	align-items: center;
	gap: 18rpx;
	margin-top: 10rpx;
}

.am-topic-card__meta-text {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}
</style>

<template>
	<view class="am-video-card" @tap="emit('tap')">
		<image class="am-video-card__cover" :src="cover" mode="aspectFill" />
		<view class="am-video-card__play">
			<text class="am-video-card__play-text">▶</text>
		</view>
		<text class="am-video-card__duration" v-if="duration">{{ duration }}</text>
		<view class="am-video-card__body" v-if="title || meta">
			<text class="am-video-card__title" v-if="title">{{ title }}</text>
			<text class="am-video-card__meta" v-if="meta">{{ meta }}</text>
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


defineProps({
	cover: {
		type: String,
		default: '',
	},
	title: {
		type: String,
		default: '',
	},
	meta: {
		type: String,
		default: '',
	},
	duration: {
		type: String,
		default: '',
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-video-card {
	position: relative;
	overflow: hidden;
	border-radius: 18rpx;
	background: var(--am-color-bg-muted);
}

.am-video-card__cover {
	width: 100%;
	height: 260rpx;
	display: block;
}

.am-video-card__play {
	position: absolute;
	left: 50%;
	top: 50%;
	width: 76rpx;
	height: 76rpx;
	border-radius: 50%;
	background: rgba(15, 23, 42, 0.56);
	transform: translate(-50%, -50%);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-video-card__play-text {
	font-size: 32rpx;
	color: #ffffff;
	line-height: 1;
}

.am-video-card__duration {
	position: absolute;
	right: 14rpx;
	bottom: 14rpx;
	padding: 4rpx 10rpx;
	border-radius: 999rpx;
	background: rgba(15, 23, 42, 0.62);
	font-size: 20rpx;
	color: #ffffff;
	line-height: 28rpx;
}

.am-video-card__body {
	padding: 18rpx;
	background: #ffffff;
}

.am-video-card__title {
	display: block;
	font-size: 26rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 36rpx;
}

.am-video-card__meta {
	display: block;
	margin-top: 6rpx;
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}
</style>

<template>
	<view class="am-media-card" :class="{ 'am-media-card--horizontal': horizontal }" @tap="emit('tap')">
		<view class="am-media-card__cover" v-if="cover">
			<image class="am-media-card__image" :src="cover" mode="aspectFill" />
			<view class="am-media-card__play" v-if="video">
				<text class="am-media-card__play-text">▶</text>
			</view>
		</view>
		<view class="am-media-card__main">
			<text class="am-media-card__title">{{ title }}</text>
			<text class="am-media-card__desc" v-if="desc">{{ desc }}</text>
			<view class="am-media-card__meta" v-if="tag || meta">
				<AmTag v-if="tag" :text="tag" plain />
				<text class="am-media-card__meta-text" v-if="meta">{{ meta }}</text>
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
	tag: {
		type: String,
		default: '',
	},
	meta: {
		type: String,
		default: '',
	},
	video: {
		type: Boolean,
		default: false,
	},
	horizontal: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-media-card {
	overflow: hidden;
	border-radius: 22rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
}

.am-media-card--horizontal {
	display: flex;
	align-items: stretch;
}

.am-media-card__cover {
	position: relative;
	height: 220rpx;
	background: var(--am-color-bg-muted);
}

.am-media-card--horizontal .am-media-card__cover {
	width: 210rpx;
	height: auto;
	min-height: 180rpx;
	flex-shrink: 0;
}

.am-media-card__image {
	width: 100%;
	height: 100%;
}

.am-media-card__play {
	position: absolute;
	left: 50%;
	top: 50%;
	width: 68rpx;
	height: 68rpx;
	border-radius: 50%;
	background: rgba(15, 23, 42, 0.56);
	transform: translate(-50%, -50%);
	display: flex;
	align-items: center;
	justify-content: center;
}

.am-media-card__play-text {
	font-size: 28rpx;
	color: #ffffff;
	line-height: 1;
}

.am-media-card__main {
	padding: 22rpx;
	box-sizing: border-box;
}

.am-media-card__title {
	display: block;
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 40rpx;
}

.am-media-card__desc {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.am-media-card__meta {
	display: flex;
	align-items: center;
	gap: 12rpx;
	margin-top: 16rpx;
}

.am-media-card__meta-text {
	font-size: 20rpx;
	color: var(--am-color-text-tertiary);
	line-height: 28rpx;
}
</style>

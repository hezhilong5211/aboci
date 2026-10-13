<template>
	<view class="am-template-card" :class="{ 'am-template-card--compact': compact }" @tap="emit('tap')">
		<view class="am-template-card__cover" v-if="cover && !compact">
			<image class="am-template-card__image" :src="cover" mode="aspectFill" />
			<text class="am-template-card__badge" v-if="badge">{{ badge }}</text>
		</view>
		<view class="am-template-card__thumb" v-if="compact && cover">
			<image class="am-template-card__thumb-image" :src="cover" mode="aspectFill" />
		</view>
		<view class="am-template-card__body">
			<view class="am-template-card__title-row">
				<text class="am-template-card__title">{{ title }}</text>
				<text class="am-template-card__status" v-if="status" :class="`am-template-card__status--${statusType}`">{{ status }}</text>
			</view>
			<text class="am-template-card__desc" v-if="desc">{{ desc }}</text>
			<view class="am-template-card__tags" v-if="tags.length && !compact">
				<text class="am-template-card__tag" v-for="tag in tags" :key="tag">{{ tag }}</text>
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
	tags: {
		type: Array,
		default: () => [],
	},
	badge: {
		type: String,
		default: '',
	},
	status: {
		type: String,
		default: '',
	},
	statusType: {
		type: String,
		default: 'success',
	},
	compact: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.am-template-card {
	overflow: hidden;
	border-radius: 24rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
}

.am-template-card--compact {
	min-height: 106rpx;
	padding: 16rpx;
	display: flex;
	align-items: center;
	gap: 16rpx;
	box-sizing: border-box;
}

.am-template-card__cover {
	position: relative;
	height: 184rpx;
	background: var(--am-color-bg-muted);
}

.am-template-card__image,
.am-template-card__thumb-image {
	width: 100%;
	height: 100%;
}

.am-template-card__badge {
	position: absolute;
	right: 16rpx;
	top: 14rpx;
	padding: 6rpx 14rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.9);
	color: var(--am-color-primary);
	font-size: 18rpx;
	font-weight: 700;
	line-height: 26rpx;
}

.am-template-card__thumb {
	width: 72rpx;
	height: 72rpx;
	border-radius: 14rpx;
	overflow: hidden;
	background: var(--am-color-bg-muted);
	flex-shrink: 0;
}

.am-template-card__body {
	padding: 22rpx;
	display: flex;
	flex-direction: column;
}

.am-template-card--compact .am-template-card__body {
	flex: 1;
	min-width: 0;
	padding: 0;
}

.am-template-card__title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
}

.am-template-card__title {
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}

.am-template-card--compact .am-template-card__title {
	font-size: 24rpx;
	line-height: 34rpx;
}

.am-template-card__status {
	font-size: 18rpx;
	line-height: 26rpx;
	flex-shrink: 0;
}

.am-template-card__status--success {
	color: #16a34a;
}

.am-template-card__status--warning {
	color: #f59f00;
}

.am-template-card__status--primary {
	color: var(--am-color-primary);
}

.am-template-card__desc {
	margin-top: 8rpx;
	font-size: 21rpx;
	color: var(--am-color-text-secondary);
	line-height: 31rpx;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.am-template-card--compact .am-template-card__desc {
	margin-top: 2rpx;
	white-space: nowrap;
	text-overflow: ellipsis;
	display: block;
}

.am-template-card__tags {
	margin-top: 18rpx;
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
}

.am-template-card__tag {
	padding: 5rpx 12rpx;
	border-radius: 999rpx;
	background: var(--am-color-primary-soft);
	color: var(--am-color-primary);
	font-size: 18rpx;
	line-height: 26rpx;
}

.am-performance-light .am-template-card {
	box-shadow: none;
}
</style>
